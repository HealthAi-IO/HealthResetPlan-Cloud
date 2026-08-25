#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [Parameter(Mandatory)]
    [SecureString]$AccessTokenA,

    [Parameter(Mandatory)]
    [SecureString]$AccessTokenB,

    [Parameter(Mandatory)]
    [switch]$ConfirmAuthorized,

    [ValidateRange(1, 500)]
    [int]$Concurrency = 100,

    [ValidateRange(1, 10000)]
    [int]$TargetRps = 50,

    [ValidateRange(10, 86400)]
    [int]$DurationSeconds = 300,

    [switch]$SkipLoad,
    [switch]$AllowProduction
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $ConfirmAuthorized) {
    throw '必须通过 -ConfirmAuthorized 明确确认已获得测试授权。'
}

$normalizedBaseUrl = $BaseUrl.Trim().TrimEnd('/')
$baseUri = [Uri]$normalizedBaseUrl
if ($baseUri.Scheme -ne 'https' -and $baseUri.Host -notin @('localhost', '127.0.0.1')) {
    throw '非本机测试地址必须使用 HTTPS。'
}
if ($baseUri.Host -eq 'api.jkcqplan.com' -and -not $AllowProduction) {
    throw '检测到生产域名。若确已批准生产测试，请显式添加 -AllowProduction。'
}
if (-not $baseUri.AbsolutePath.TrimEnd('/').EndsWith('/api/v1')) {
    throw 'BaseUrl 必须包含 /api/v1，例如 https://staging.example.com/api/v1。'
}
if ($TargetRps -gt ($Concurrency * 10)) {
    throw 'TargetRps 相对并发数过高，请增加 Concurrency 或降低 TargetRps。'
}

$tokenA = [Net.NetworkCredential]::new('', $AccessTokenA).Password
$tokenB = [Net.NetworkCredential]::new('', $AccessTokenB).Password
if ([string]::IsNullOrWhiteSpace($tokenA) -or [string]::IsNullOrWhiteSpace($tokenB)) {
    throw '两个 Access Token 均不能为空。'
}

$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $workspaceRoot "outputs\app-api-test-$runId"
[IO.Directory]::CreateDirectory($outputDir) | Out-Null

$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$http = [Net.Http.HttpClient]::new($handler)
$http.Timeout = [TimeSpan]::FromSeconds(20)
$http.DefaultRequestHeaders.UserAgent.ParseAdd('HealthResetPlan-Authorized-Test/1.0')

$checks = [Collections.Generic.List[object]]::new()

function Get-HeaderValue {
    param([Parameter(Mandatory)]$Response, [Parameter(Mandatory)][string]$Name)
    foreach ($header in $Response.Headers) {
        if ($header.Key -ieq $Name) {
            return (@($header.Value | Select-Object -Unique) -join ', ')
        }
    }
    if ($Response.Content) {
        foreach ($header in $Response.Content.Headers) {
            if ($header.Key -ieq $Name) {
                return (@($header.Value | Select-Object -Unique) -join ', ')
            }
        }
    }
    return ''
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Token,
        [object]$Body,
        [hashtable]$Headers
    )

    $request = [Net.Http.HttpRequestMessage]::new(
        [Net.Http.HttpMethod]::new($Method),
        "$normalizedBaseUrl$Path"
    )
    try {
        if (-not [string]::IsNullOrWhiteSpace($Token)) {
            $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        }
        if ($Headers) {
            foreach ($entry in $Headers.GetEnumerator()) {
                [void]$request.Headers.TryAddWithoutValidation($entry.Key, [string]$entry.Value)
            }
        }
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 20 -Compress
            $request.Content = [Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
        }

        $timer = [Diagnostics.Stopwatch]::StartNew()
        try {
            $response = $http.SendAsync($request).GetAwaiter().GetResult()
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $timer.Stop()
            $jsonBody = $null
            if (-not [string]::IsNullOrWhiteSpace($text) -and $text.TrimStart().StartsWith(('{'))) {
                try { $jsonBody = $text | ConvertFrom-Json -Depth 50 } catch { $jsonBody = $null }
            }
            return [pscustomobject]@{
                Status = [int]$response.StatusCode
                DurationMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 2)
                Json = $jsonBody
                Text = $text
                Response = $response
                Error = ''
            }
        }
        catch {
            $timer.Stop()
            return [pscustomobject]@{
                Status = 0
                DurationMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 2)
                Json = $null
                Text = ''
                Response = $null
                Error = $_.Exception.Message
            }
        }
    }
    finally {
        $request.Dispose()
    }
}

function Invoke-MultipartUpload {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Token,
        [Parameter(Mandatory)][string]$ClientId,
        [Parameter(Mandatory)][byte[]]$Bytes
    )

    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Post, "$normalizedBaseUrl$Path")
    $multipart = [Net.Http.MultipartFormDataContent]::new()
    $fileContent = [Net.Http.ByteArrayContent]::new($Bytes)
    $fileContent.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('application/octet-stream')
    $multipart.Add($fileContent, 'file', 'authorized-test.bin')
    $multipart.Add([Net.Http.StringContent]::new($ClientId), 'clientId')
    $request.Content = $multipart
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    try {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $response = $http.SendAsync($request).GetAwaiter().GetResult()
        $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $timer.Stop()
        $jsonBody = $null
        try { $jsonBody = $text | ConvertFrom-Json -Depth 50 } catch { $jsonBody = $null }
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            DurationMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 2)
            Json = $jsonBody
            Text = $text
            Response = $response
            Error = ''
        }
    }
    catch {
        return [pscustomobject]@{ Status = 0; DurationMs = 0; Json = $null; Text = ''; Response = $null; Error = $_.Exception.Message }
    }
    finally {
        $request.Dispose()
    }
}

function Test-ApiSuccess {
    param($Result)
    if ($Result.Status -lt 200 -or $Result.Status -ge 300) { return $false }
    if ($null -ne $Result.Json -and $null -ne $Result.Json.PSObject.Properties['code']) {
        return [int]$Result.Json.code -eq 0
    }
    return $true
}

function Test-ApiDenied {
    param($Result)
    if ($Result.Status -in 401, 403, 404, 405) { return $true }
    if ($null -ne $Result.Json -and $null -ne $Result.Json.PSObject.Properties['code']) {
        $code = [int]$Result.Json.code
        return $code -ge 40100 -and $code -lt 40500
    }
    return $false
}

function Add-Check {
    param(
        [Parameter(Mandatory)][string]$Area,
        [Parameter(Mandatory)][string]$Name,
        [AllowNull()][Nullable[bool]]$Passed,
        [ValidateSet('critical', 'high', 'medium', 'low', 'info')][string]$Severity = 'medium',
        [string]$Evidence = ''
    )
    $status = if ($null -eq $Passed) { 'SKIP' } elseif ($Passed) { 'PASS' } else { 'FAIL' }
    $checks.Add([pscustomobject]@{
        Area = $Area
        Test = $Name
        Status = $status
        Severity = $Severity
        Evidence = $Evidence
    })
    $color = if ($status -eq 'PASS') { 'Green' } elseif ($status -eq 'FAIL') { 'Red' } else { 'Yellow' }
    Write-Host "[$status] $Area - $Name" -ForegroundColor $color
}

function Find-FirstValue {
    param([object]$Value, [Parameter(Mandatory)][string]$PropertyName)
    if ($null -eq $Value) { return $null }
    if ($Value -is [Collections.IDictionary]) {
        if ($Value.Contains($PropertyName)) { return $Value[$PropertyName] }
        foreach ($child in $Value.Values) {
            $found = Find-FirstValue -Value $child -PropertyName $PropertyName
            if ($null -ne $found) { return $found }
        }
    }
    elseif ($Value -is [Collections.IEnumerable] -and $Value -isnot [string]) {
        foreach ($child in $Value) {
            $found = Find-FirstValue -Value $child -PropertyName $PropertyName
            if ($null -ne $found) { return $found }
        }
    }
    elseif ($Value -is [string] -or $Value -is [ValueType]) {
        return $null
    }
    else {
        $property = $Value.PSObject.Properties[$PropertyName]
        if ($null -ne $property) { return $property.Value }
        foreach ($propertyItem in $Value.PSObject.Properties) {
            $found = Find-FirstValue -Value $propertyItem.Value -PropertyName $PropertyName
            if ($null -ne $found) { return $found }
        }
    }
    return $null
}

try {
    Write-Host "测试目标: $normalizedBaseUrl" -ForegroundColor Cyan
    Write-Host '模式: Safe+Write（排除短信、AI、支付、账号和云数据破坏操作）' -ForegroundColor Cyan

    $health = Invoke-Api -Method GET -Path '/health'
    Add-Check -Area '可用性' -Name '公开健康检查' -Passed (Test-ApiSuccess $health) -Severity high -Evidence "HTTP $($health.Status), $($health.DurationMs) ms"

    $latest = Invoke-Api -Method GET -Path '/releases/latest?platform=android&channel=official'
    Add-Check -Area '公开接口' -Name '版本信息可访问' -Passed (Test-ApiSuccess $latest) -Severity low -Evidence "HTTP $($latest.Status)"

    $cors = Invoke-Api -Method GET -Path '/health' -Headers @{ Origin = 'https://attacker.invalid' }
    $allowedOrigin = if ($cors.Response) { Get-HeaderValue -Response $cors.Response -Name 'Access-Control-Allow-Origin' } else { '' }
    Add-Check -Area 'CORS' -Name '不反射非信任 Origin' -Passed ($allowedOrigin -ne 'https://attacker.invalid' -and $allowedOrigin -ne '*') -Severity high -Evidence "Access-Control-Allow-Origin=$allowedOrigin"

    if ($baseUri.Scheme -eq 'https') {
        $hsts = if ($health.Response) { Get-HeaderValue -Response $health.Response -Name 'Strict-Transport-Security' } else { '' }
        Add-Check -Area '安全响应头' -Name '启用 HSTS' -Passed (-not [string]::IsNullOrWhiteSpace($hsts)) -Severity medium -Evidence "Strict-Transport-Security=$hsts"
    }
    $nosniff = if ($health.Response) { Get-HeaderValue -Response $health.Response -Name 'X-Content-Type-Options' } else { '' }
    Add-Check -Area '安全响应头' -Name '禁止 MIME 嗅探' -Passed ($nosniff -eq 'nosniff') -Severity medium -Evidence "X-Content-Type-Options=$nosniff"

    $protectedRoutes = @(
        '/users/me', '/data', '/reports?page=1&size=1', '/content?page=1&size=1',
        '/messages?page=1&size=1', '/push/config', '/ai/consent', '/ai/chat/daily-usage',
        '/ai-credits/balance', '/files/content?objectKey=files%2Funknown%2Ftest.enc'
    )
    foreach ($path in $protectedRoutes) {
        $result = Invoke-Api -Method GET -Path $path
        Add-Check -Area '鉴权矩阵' -Name "未登录拒绝 GET $path" -Passed ($result.Status -eq 401) -Severity high -Evidence "HTTP $($result.Status)"
    }

    $badJwt = Invoke-Api -Method GET -Path '/users/me' -Token 'abc.def.ghi'
    Add-Check -Area 'JWT' -Name '拒绝畸形 JWT' -Passed ($badJwt.Status -eq 401) -Severity high -Evidence "HTTP $($badJwt.Status)"

    $meA = Invoke-Api -Method GET -Path '/users/me' -Token $tokenA
    $meB = Invoke-Api -Method GET -Path '/users/me' -Token $tokenB
    $userA = Find-FirstValue -Value $meA.Json -PropertyName 'userId'
    $userB = Find-FirstValue -Value $meB.Json -PropertyName 'userId'
    $nicknameA = Find-FirstValue -Value $meA.Json -PropertyName 'nickname'
    Add-Check -Area '测试账号' -Name '账号 A Token 有效' -Passed (Test-ApiSuccess $meA) -Severity critical -Evidence "HTTP $($meA.Status)"
    Add-Check -Area '测试账号' -Name '账号 B Token 有效' -Passed (Test-ApiSuccess $meB) -Severity critical -Evidence "HTTP $($meB.Status)"
    Add-Check -Area '测试账号' -Name '两个 Token 属于不同用户' -Passed (-not [string]::IsNullOrWhiteSpace([string]$userA) -and $userA -ne $userB) -Severity critical -Evidence '仅比较 userId，未写入报告'
    if (-not (Test-ApiSuccess $meA) -or -not (Test-ApiSuccess $meB) -or $userA -eq $userB) {
        throw '双账号前置检查失败，停止写入与压测。'
    }

    $admin = Invoke-Api -Method GET -Path '/admin/dashboard' -Token $tokenA
    Add-Check -Area '权限提升' -Name '普通用户不能访问管理端' -Passed ($admin.Status -eq 403) -Severity critical -Evidence "HTTP $($admin.Status)"

    $massAssignment = Invoke-Api -Method PUT -Path '/users/me' -Token $tokenA -Body @{
        nickname = [string]$nicknameA
        userId = 'security-test-forbidden'
        role = 'SUPER_ADMIN'
        isAdmin = $true
    }
    $meAfterMassAssignment = Invoke-Api -Method GET -Path '/users/me' -Token $tokenA
    $userAfter = Find-FirstValue -Value $meAfterMassAssignment.Json -PropertyName 'userId'
    $adminAfter = Invoke-Api -Method GET -Path '/admin/dashboard' -Token $tokenA
    Add-Check -Area '批量赋值' -Name '禁止通过资料接口修改身份/角色' -Passed ($massAssignment.Status -lt 500 -and $userAfter -eq $userA -and $adminAfter.Status -eq 403) -Severity critical -Evidence "update HTTP $($massAssignment.Status), admin HTTP $($adminAfter.Status)"

    $traversal = Invoke-Api -Method GET -Path '/content/assets?objectKey=../../application.yml&contentType=text/plain'
    Add-Check -Area '路径穿越' -Name '内容素材接口拒绝目录穿越' -Passed (Test-ApiDenied $traversal) -Severity critical -Evidence "HTTP $($traversal.Status)"

    $injection = [Uri]::EscapeDataString("' OR 1=1 --")
    $sqlProbe = Invoke-Api -Method GET -Path "/content?type=$injection&page=1&size=5" -Token $tokenA
    Add-Check -Area '注入与异常处理' -Name 'SQL 型输入不触发服务端错误' -Passed ($sqlProbe.Status -lt 500 -and $sqlProbe.Status -gt 0) -Severity high -Evidence "HTTP $($sqlProbe.Status)"

    $boundary = Invoke-Api -Method GET -Path '/content?page=-1&size=1000000' -Token $tokenA
    Add-Check -Area '参数边界' -Name '异常分页不触发服务端错误' -Passed ($boundary.Status -lt 500 -and $boundary.Status -gt 0) -Severity medium -Evidence "HTTP $($boundary.Status)"

    $wrongMethod = Invoke-Api -Method TRACE -Path '/users/me' -Token $tokenA
    Add-Check -Area 'HTTP 方法' -Name '拒绝 TRACE' -Passed (Test-ApiDenied $wrongMethod) -Severity medium -Evidence "HTTP $($wrongMethod.Status)"

    $fileClientId = "security-test-$runId-$([Guid]::NewGuid().ToString('N'))"
    $fileBytes = [Text.Encoding]::UTF8.GetBytes("authorized-security-test-$runId")
    $upload = Invoke-MultipartUpload -Path '/files/upload' -Token $tokenA -ClientId $fileClientId -Bytes $fileBytes
    $objectKey = Find-FirstValue -Value $upload.Json -PropertyName 'objectKey'
    if (Test-ApiSuccess $upload -and -not [string]::IsNullOrWhiteSpace([string]$objectKey)) {
        $encodedKey = [Uri]::EscapeDataString([string]$objectKey)
        $downloadA = Invoke-Api -Method GET -Path "/files/content?objectKey=$encodedKey" -Token $tokenA
        $downloadB = Invoke-Api -Method GET -Path "/files/content?objectKey=$encodedKey" -Token $tokenB
        $deleteB = Invoke-Api -Method DELETE -Path "/files?objectKey=$encodedKey" -Token $tokenB
        $downloadAAfter = Invoke-Api -Method GET -Path "/files/content?objectKey=$encodedKey" -Token $tokenA
        Add-Check -Area '文件写入' -Name '本人可上传并读取测试文件' -Passed ($downloadA.Status -eq 200 -and $downloadA.Text -eq [Text.Encoding]::UTF8.GetString($fileBytes)) -Severity high -Evidence "upload HTTP $($upload.Status), download HTTP $($downloadA.Status)"
        Add-Check -Area '文件越权' -Name '账号 B 不能读取账号 A 文件' -Passed (Test-ApiDenied $downloadB) -Severity critical -Evidence "HTTP $($downloadB.Status)"
        Add-Check -Area '文件越权' -Name '账号 B 不能删除账号 A 文件' -Passed ((Test-ApiDenied $deleteB) -and $downloadAAfter.Status -eq 200) -Severity critical -Evidence "delete HTTP $($deleteB.Status), owner recheck HTTP $($downloadAAfter.Status)"
        $cleanupFile = Invoke-Api -Method DELETE -Path "/files?objectKey=$encodedKey" -Token $tokenA
        Add-Check -Area '清理' -Name '删除测试文件' -Passed (Test-ApiSuccess $cleanupFile) -Severity info -Evidence "HTTP $($cleanupFile.Status)"
    }
    else {
        Add-Check -Area '文件写入' -Name '文件上传与越权链路' -Passed $null -Severity high -Evidence "上传失败 HTTP $($upload.Status)"
    }

    $reportClientId = "security-test-$runId-$([Guid]::NewGuid().ToString('N'))"
    $saveReport = Invoke-Api -Method POST -Path '/reports' -Token $tokenA -Body @{
        clientId = $reportClientId
        reportTime = (Get-Date).ToUniversalTime().ToString('o')
        deviceId = "security-test-$runId"
        clientUpdatedAt = (Get-Date).ToUniversalTime().ToString('o')
        alg = 'aes-256-gcm:v1'
    }
    if (Test-ApiSuccess $saveReport) {
        $deleteReportB = Invoke-Api -Method DELETE -Path "/reports/$reportClientId" -Token $tokenB
        $reportsA = Invoke-Api -Method GET -Path '/reports?page=1&size=100' -Token $tokenA
        $ownerStillHasReport = $reportsA.Text.Contains($reportClientId, [StringComparison]::Ordinal)
        Add-Check -Area '报告越权' -Name '账号 B 不能删除账号 A 报告' -Passed $ownerStillHasReport -Severity critical -Evidence "cross-delete HTTP $($deleteReportB.Status), owner record retained=$ownerStillHasReport"
        $cleanupReport = Invoke-Api -Method DELETE -Path "/reports/$reportClientId" -Token $tokenA
        Add-Check -Area '清理' -Name '删除测试报告' -Passed (Test-ApiSuccess $cleanupReport) -Severity info -Evidence "HTTP $($cleanupReport.Status)"
    }
    else {
        Add-Check -Area '报告写入' -Name '报告写入与越权链路' -Passed $null -Severity high -Evidence "写入失败 HTTP $($saveReport.Status)"
    }

    $contentList = Invoke-Api -Method GET -Path '/content?page=1&size=1' -Token $tokenA
    $contentId = Find-FirstValue -Value (Find-FirstValue -Value $contentList.Json -PropertyName 'items') -PropertyName 'id'
    if ($null -ne $contentId) {
        $commentMarker = "authorized-security-test-$runId-$([Guid]::NewGuid().ToString('N'))"
        $addComment = Invoke-Api -Method POST -Path "/content/$contentId/comments" -Token $tokenA -Body @{ content = $commentMarker }
        $commentId = $null
        if (Test-ApiSuccess $addComment) {
            $comments = Find-FirstValue -Value $addComment.Json -PropertyName 'comments'
            foreach ($comment in @($comments)) {
                if ([string](Find-FirstValue -Value $comment -PropertyName 'content') -eq $commentMarker) {
                    $commentId = Find-FirstValue -Value $comment -PropertyName 'id'
                    break
                }
            }
        }
        if ($null -ne $commentId) {
            $deleteCommentB = Invoke-Api -Method DELETE -Path "/content/$contentId/comments/$commentId" -Token $tokenB
            $interactionA = Invoke-Api -Method GET -Path "/content/$contentId/interactions" -Token $tokenA
            $commentRetained = $interactionA.Text.Contains($commentMarker, [StringComparison]::Ordinal)
            Add-Check -Area '评论越权' -Name '账号 B 不能删除账号 A 评论' -Passed $commentRetained -Severity critical -Evidence "cross-delete HTTP $($deleteCommentB.Status), owner comment retained=$commentRetained"
            $cleanupComment = Invoke-Api -Method DELETE -Path "/content/$contentId/comments/$commentId" -Token $tokenA
            Add-Check -Area '清理' -Name '删除测试评论' -Passed (Test-ApiSuccess $cleanupComment) -Severity info -Evidence "HTTP $($cleanupComment.Status)"
        }
        else {
            Add-Check -Area '评论写入' -Name '评论写入与越权链路' -Passed $null -Severity medium -Evidence "未获得测试 commentId，HTTP $($addComment.Status)"
        }
    }
    else {
        Add-Check -Area '评论写入' -Name '评论写入与越权链路' -Passed $null -Severity medium -Evidence '预发布环境没有已发布内容'
    }

    $deviceId = "security-test-$runId"
    $pushHeaders = @{ 'X-Device-Id' = $deviceId }
    $pushPublicKey = [Convert]::ToBase64String([byte[]](0..64)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $pushAuth = [Convert]::ToBase64String([byte[]](0..15)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $subscribe = Invoke-Api -Method PUT -Path '/push/subscription' -Token $tokenA -Headers $pushHeaders -Body @{
        endpoint = "https://fcm.googleapis.com/fcm/send/security-test-$runId"
        p256dh = $pushPublicKey
        auth = $pushAuth
        timezone = 'Asia/Shanghai'
    }
    $unsubscribe = Invoke-Api -Method DELETE -Path '/push/subscription' -Token $tokenA -Headers $pushHeaders
    Add-Check -Area '推送写入' -Name '订阅写入后可清理' -Passed ((Test-ApiSuccess $subscribe) -and (Test-ApiSuccess $unsubscribe)) -Severity medium -Evidence "PUT $($subscribe.Status), DELETE $($unsubscribe.Status)"

    $readOnlyRoutes = @(
        '/data', '/reports?page=1&size=20', '/messages?page=1&size=20',
        '/messages/unread-count', '/ai/consent', '/ai/chat/daily-usage',
        '/ai-credits/products', '/ai-credits/balance', '/ai-credits/ledger',
        '/ai-credits/orders', '/push/config'
    )
    foreach ($path in $readOnlyRoutes) {
        $readResult = Invoke-Api -Method GET -Path $path -Token $tokenA
        Add-Check -Area '只读回归' -Name "GET $path" -Passed (Test-ApiSuccess $readResult) -Severity medium -Evidence "HTTP $($readResult.Status), $($readResult.DurationMs) ms"
    }

    $loadResults = @()
    if (-not $SkipLoad) {
        Write-Host "开始压测: 并发=$Concurrency, 目标RPS=$TargetRps, 时长=${DurationSeconds}s" -ForegroundColor Cyan
        $loadRoutes = @(
            '/health',
            '/users/me',
            '/content?page=1&size=12',
            '/data',
            '/reports?page=1&size=20',
            '/messages?page=1&size=20',
            '/messages/unread-count',
            '/ai/chat/daily-usage',
            '/ai-credits/products',
            '/ai-credits/balance',
            '/push/config',
            '/releases/check?platform=android&currentVersion=1.0.0&channel=official&deviceId=load-test'
        )
        $baseForLoad = $normalizedBaseUrl
        $loadResults = 1..$Concurrency | ForEach-Object -Parallel {
            $worker = $_
            $routes = $using:loadRoutes
            $base = $using:baseForLoad
            $duration = $using:DurationSeconds
            $workers = $using:Concurrency
            $rps = $using:TargetRps
            $a = $using:tokenA
            $b = $using:tokenB
            $token = if (($worker % 2) -eq 0) { $a } else { $b }
            $intervalMs = [math]::Max(1.0, (1000.0 * $workers / $rps))
            Start-Sleep -Milliseconds (Get-Random -Minimum 0 -Maximum ([int][math]::Max(1, $intervalMs)))
            $deadline = [DateTime]::UtcNow.AddSeconds($duration)
            $client = [Net.Http.HttpClient]::new()
            $client.Timeout = [TimeSpan]::FromSeconds(20)
            $client.DefaultRequestHeaders.UserAgent.ParseAdd('HealthResetPlan-Authorized-Load/1.0')
            $client.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
            $client.DefaultRequestHeaders.TryAddWithoutValidation('X-Device-Id', "load-test-$worker") | Out-Null
            $client.DefaultRequestHeaders.TryAddWithoutValidation('X-Platform', 'windows') | Out-Null
            $client.DefaultRequestHeaders.TryAddWithoutValidation('X-App-Version', 'security-test') | Out-Null
            try {
                while ([DateTime]::UtcNow -lt $deadline) {
                    $path = $routes[($worker + (Get-Random -Minimum 0 -Maximum $routes.Count)) % $routes.Count]
                    $timer = [Diagnostics.Stopwatch]::StartNew()
                    $status = 0
                    $errorText = ''
                    try {
                        $response = $client.GetAsync("$base$path").GetAwaiter().GetResult()
                        $status = [int]$response.StatusCode
                        $response.Dispose()
                    }
                    catch {
                        $errorText = $_.Exception.GetType().Name
                    }
                    $timer.Stop()
                    [pscustomobject]@{
                        TimestampUtc = [DateTime]::UtcNow.ToString('o')
                        Worker = $worker
                        Path = $path
                        Status = $status
                        DurationMs = [math]::Round($timer.Elapsed.TotalMilliseconds, 2)
                        Error = $errorText
                    }
                    $remaining = [int][math]::Round($intervalMs - $timer.Elapsed.TotalMilliseconds)
                    if ($remaining -gt 0) { Start-Sleep -Milliseconds $remaining }
                }
            }
            finally {
                $client.Dispose()
            }
        } -ThrottleLimit $Concurrency

        $loadResults | Export-Csv -LiteralPath (Join-Path $outputDir 'load-results.csv') -NoTypeInformation -Encoding utf8
    }

    $checks | Export-Csv -LiteralPath (Join-Path $outputDir 'security-functional-results.csv') -NoTypeInformation -Encoding utf8

    $failed = @($checks | Where-Object Status -eq 'FAIL')
    $passed = @($checks | Where-Object Status -eq 'PASS')
    $skipped = @($checks | Where-Object Status -eq 'SKIP')
    $loadCount = @($loadResults).Count
    $loadFailures = @($loadResults | Where-Object { $_.Status -lt 200 -or $_.Status -ge 300 }).Count
    $latencies = @($loadResults | Where-Object Status -ge 100 | ForEach-Object DurationMs | Sort-Object)
    $p50 = if ($latencies.Count) { $latencies[[math]::Max(0, [math]::Ceiling($latencies.Count * 0.50) - 1)] } else { 0 }
    $p95 = if ($latencies.Count) { $latencies[[math]::Max(0, [math]::Ceiling($latencies.Count * 0.95) - 1)] } else { 0 }
    $p99 = if ($latencies.Count) { $latencies[[math]::Max(0, [math]::Ceiling($latencies.Count * 0.99) - 1)] } else { 0 }
    $errorRate = if ($loadCount) { [math]::Round(100.0 * $loadFailures / $loadCount, 3) } else { 0 }
    $actualRps = if ($loadCount -and -not $SkipLoad) { [math]::Round($loadCount / $DurationSeconds, 2) } else { 0 }

    $summary = [ordered]@{
        runId = $runId
        target = $normalizedBaseUrl
        mode = 'Safe+Write'
        functional = [ordered]@{ passed = $passed.Count; failed = $failed.Count; skipped = $skipped.Count }
        load = [ordered]@{
            skipped = [bool]$SkipLoad
            concurrency = $Concurrency
            targetRps = $TargetRps
            durationSeconds = $DurationSeconds
            requests = $loadCount
            actualRps = $actualRps
            errorRatePercent = $errorRate
            p50Ms = $p50
            p95Ms = $p95
            p99Ms = $p99
        }
    }
    $summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $outputDir 'summary.json') -Encoding utf8

    $failureLines = if ($failed.Count) {
        ($failed | ForEach-Object { "- [$($_.Severity)] $($_.Area) / $($_.Test): $($_.Evidence)" }) -join "`n"
    } else {
        '- 无'
    }
    $report = @"
# APP 接口安全与压测报告

- 执行时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')
- 目标：$normalizedBaseUrl
- 模式：Safe+Write
- 功能/安全检查：通过 $($passed.Count)，失败 $($failed.Count)，跳过 $($skipped.Count)
- 压测：并发 $Concurrency，目标 $TargetRps RPS，持续 $DurationSeconds 秒
- 实际请求：$loadCount，实际 $actualRps RPS，错误率 $errorRate%
- 延迟：P50 $p50 ms，P95 $p95 ms，P99 $p99 ms

## 未通过检查

$failureLines

详细数据见 `security-functional-results.csv` 和 `load-results.csv`。报告不包含 Access Token、密码、手机号或响应业务数据。
"@
    $report | Set-Content -LiteralPath (Join-Path $outputDir 'report.md') -Encoding utf8

    Write-Host "完成。报告目录: $outputDir" -ForegroundColor Cyan
    if ($failed.Count -gt 0) { exit 2 }
}
finally {
    $tokenA = $null
    $tokenB = $null
    $http.Dispose()
    $handler.Dispose()
}
