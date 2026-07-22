# HealthResetPlan-Cloud

健康重启计划后端 API：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8。

## 本地启动

在项目根目录运行：

```powershell
.\start-backend.cmd
```

或进入后端目录手动运行：

```powershell
..\tools\maven\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=..\.m2repo" spring-boot:run
```

健康检查：

```text
http://localhost:8080/api/v1/health
```

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 凭据 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 主机 |
| `JWT_SECRET` | JWT 签名密钥 |
| `SMS_ENABLED` | 是否启用真实短信发送，生产环境填 `true` |
| `SMS_DEBUG_CODE_ENABLED` | 是否在接口响应中返回调试验证码，生产环境必须为 `false` |
| `JDCLOUD_ACCESS_KEY_ID` / `JDCLOUD_SECRET_ACCESS_KEY` | 京东云访问密钥 |
| `JDCLOUD_SMS_SIGN_ID` / `JDCLOUD_SMS_TEMPLATE_ID` | 京东云短信审核通过后的签名 ID / 模板 ID |
| `SMS_CODE_TTL_SECONDS` / `SMS_RESEND_INTERVAL_SECONDS` | 验证码有效期 / 单手机号发送间隔 |
| `SMS_MAX_PER_PHONE_PER_HOUR` / `SMS_MAX_PER_PHONE_PER_DAY` | 单手机号每小时 / 每日发送上限 |
| `AI_CHAT_QWEN_API_KEY` / `AI_CHAT_VOLCENGINE_API_KEY` | 千问 / 火山方舟 Key |
| `AI_CHAT_QWEN_API_BASE` / `AI_CHAT_DOUBAO_API_BASE` / `AI_CHAT_GLM_API_BASE` / `AI_CHAT_DEEPSEEK_API_BASE` | AI 厂商 OpenAI 兼容接口地址 |
| `AI_CHAT_QWEN_MODEL` / `AI_CHAT_DOUBAO_MODEL` / `AI_CHAT_GLM_MODEL` / `AI_CHAT_DEEPSEEK_MODEL` | AI 模型名称或接入点 ID |
| `AI_PLAN_CACHE_MINUTES` | 7 天健康规划缓存分钟数，默认 30 |
| `AI_PLAN_MAX_COMPLETION_TOKENS` | 7 天健康规划最大输出 token，默认 4096 |

## 配置位置

AI 配置位于：

- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

后端代码通过 `app.ai.providers.*` 读取这些配置。

项目根目录的本地密钥文件 `application-local.yml` 已被 Git 忽略，并通过外部配置导入，不会打入构建产物。生产环境仍应使用环境变量。

## 管理后台安全边界

- 管理员账号保存在 `admin_account`，会话保存在 `admin_session`。
- 管理员 JWT 包含 `actorType=admin`；普通用户 JWT 不会获得 `ROLE_ADMIN`。
- 超级管理员、运营和审计角色从 `admin_role` 加载权限。
- 登录、退出、发布和工单处理等写操作记录到 `audit_log`。
- 配置了 `totp_secret` 的管理员必须通过 TOTP 动态验证码校验。

客户端公开版本检查接口：

```text
GET /api/v1/releases/check
```

## 短信验证码

密码重置接口 `/api/v1/auth/password-reset/send-code` 已接入 Redis 验证码缓存和手机号防刷限流。

京东云短信 SDK 调用代码已预写在 `src/main/java/io/healthresetplan/modules/sms/JdcloudSmsSender.java`。备案和短信签名/模板审核通过后，生产环境填入：

- `SMS_ENABLED=true`
- `JDCLOUD_ACCESS_KEY_ID`
- `JDCLOUD_SECRET_ACCESS_KEY`
- `JDCLOUD_SMS_SIGN_ID`
- `JDCLOUD_SMS_TEMPLATE_ID`

模板变量按一个参数传入，默认第一个参数为 6 位验证码。
开发环境默认 `SMS_ENABLED=false` 且 `SMS_DEBUG_CODE_ENABLED=true`，不会真实发送短信，会返回调试验证码；生产环境请保持 `SMS_DEBUG_CODE_ENABLED=false`。
