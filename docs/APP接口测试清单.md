# APP 接口测试清单

扫描范围：Flutter APP `HealthResetPlan` 与 Spring Boot 服务 `HealthResetPlan-Cloud` 当前源码。统一前缀为 `/api/v1`，共 74 个接口；不含 `/api/v1/admin/**` 管理端接口。

鉴权标记：`公开` 表示无需用户 JWT；`用户` 表示需要 `Authorization: Bearer <accessToken>`。风险标记用于控制自动化脚本，`费用` 和 `破坏` 接口不会被 Safe+Write 脚本调用。

## 认证与账号（18）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| POST | `/auth/social/wechat` | 公开 | 微信登录，外部调用 |
| POST | `/auth/social/verify-phone` | 公开 | 绑定手机号，短信/写入 |
| POST | `/auth/sms/register` | 公开 | 注册账号，写入 |
| POST | `/auth/login` | 公开 | 密码登录，必须携带验证码票据 |
| POST | `/auth/sms/send-code` | 公开 | 费用：发送短信 |
| POST | `/auth/sms/verify` | 公开 | 验证手机号 |
| POST | `/auth/password-reset/send-code` | 公开 | 费用：发送短信 |
| POST | `/auth/password-reset/reset` | 公开 | 破坏：修改密码 |
| POST | `/auth/password/set` | 用户 | 破坏：设置初始密码 |
| POST | `/auth/sms/login` | 公开 | 短信登录 |
| POST | `/auth/refresh` | 公开 | 轮换访问令牌 |
| POST | `/auth/logout` | 公开 | 使刷新令牌失效 |
| POST | `/auth/cancel-account` | 用户 | 破坏：注销账号 |
| POST | `/auth/cancel-account/send-code` | 用户 | 费用：注销验证码 |
| POST | `/auth/account-recovery/send-code` | 公开 | 费用：恢复验证码 |
| POST | `/auth/account-recovery/reactivate` | 公开 | 恢复已注销账号 |
| POST | `/auth/captcha/create` | 公开 | 创建滑块验证码 |
| POST | `/auth/captcha/verify` | 公开 | 校验滑块轨迹 |

## 用户、文件和云数据（15）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| GET | `/users/me` | 用户 | 当前用户资料 |
| POST | `/users/me/wechat` | 用户 | 绑定微信，外部调用/写入 |
| PUT | `/users/me` | 用户 | 更新资料，写入 |
| POST | `/files/upload` | 用户 | 通用文件上传，写入 |
| POST | `/files/avatar` | 用户 | 头像上传，写入 |
| GET | `/files/content` | 用户 | 下载本人文件 |
| GET | `/files/avatar` | 用户 | 读取头像 |
| DELETE | `/files` | 用户 | 删除本人文件 |
| GET | `/data` | 用户 | 读取云同步快照 |
| PUT | `/data` | 用户 | 覆盖云同步快照，写入 |
| DELETE | `/data` | 用户 | 破坏：清空云同步数据 |
| POST | `/telemetry/events` | 用户 | 写入客户端埋点 |
| GET | `/push/config` | 用户 | Web Push 配置 |
| PUT | `/push/subscription` | 用户 | 写入推送订阅 |
| DELETE | `/push/subscription` | 用户 | 删除当前设备推送订阅 |

## 健康报告（5）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| POST | `/reports/analyze` | 用户 | 费用：上传图片并调用视觉 AI |
| POST | `/reports/analyze-stored` | 用户 | 费用：分析已上传图片 |
| POST | `/reports` | 用户 | 保存加密报告，写入 |
| GET | `/reports` | 用户 | 分页读取本人报告 |
| DELETE | `/reports/{clientId}` | 用户 | 删除本人报告 |

## 健康内容与站内信（12）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| GET | `/content` | 用户 | 已发布内容列表 |
| GET | `/content/{id}` | 用户 | 内容详情 |
| POST | `/content/{id}/read` | 用户 | 写入阅读状态 |
| GET | `/content/{id}/interactions` | 用户 | 点赞与评论数据 |
| PUT | `/content/{id}/reaction` | 用户 | 点赞/点踩，写入 |
| POST | `/content/{id}/comments` | 用户 | 新增评论，写入 |
| DELETE | `/content/{id}/comments/{commentId}` | 用户 | 删除本人评论 |
| GET | `/messages` | 用户 | 站内信列表 |
| GET | `/messages/unread-count` | 用户 | 未读数量 |
| POST | `/messages/{id}/read` | 用户 | 写入已读状态 |
| POST | `/messages/read-all` | 用户 | 写入全部已读 |
| GET | `/content/assets` | 公开 | 内容素材读取；重点检查路径穿越 |

## AI（12）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| GET | `/ai/consent` | 用户 | 查询 AI 授权状态 |
| POST | `/ai/consent` | 用户 | 写入 AI 授权 |
| DELETE | `/ai/consent` | 用户 | 撤销 AI 授权 |
| POST | `/ai/chat` | 用户 | 费用：AI 对话 |
| POST | `/ai/chat/stream` | 用户 | 费用：SSE AI 对话 |
| GET | `/ai/chat/daily-usage` | 用户 | 查询每日用量 |
| POST | `/ai/plan/generate` | 用户 | 费用：生成计划 |
| POST | `/ai/wellness/menu/generate` | 用户 | 费用：生成菜单 |
| POST | `/ai/wellness/menu/swap` | 用户 | 费用：替换餐次 |
| POST | `/ai/wellness/weekly-report/generate` | 用户 | 费用：生成周报 |
| POST | `/ai/vision/analyze` | 用户 | 费用：上传图片分析 |
| POST | `/ai/vision/analyze-stored` | 用户 | 费用：分析已存图片 |

## AI 积分与支付（9）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| GET | `/ai-credits/products` | 用户 | 商品列表 |
| GET | `/ai-credits/balance` | 用户 | 积分余额 |
| GET | `/ai-credits/ledger` | 用户 | 积分流水 |
| GET | `/ai-credits/orders` | 用户 | 本人订单列表 |
| POST | `/ai-credits/orders` | 用户 | 费用：创建支付订单 |
| GET | `/ai-credits/orders/{orderNo}` | 用户 | 订单详情，重点检查越权 |
| POST | `/ai-credits/refunds` | 用户 | 破坏：申请退款 |
| POST | `/payments/wechat/notify` | 公开 | 支付回调，仅允许无效签名负向测试 |
| POST | `/payments/alipay/notify` | 公开 | 支付回调，仅允许无效签名负向测试 |

## 发布与健康检查（3）

| 方法 | 路径 | 鉴权 | 风险/说明 |
|---|---|---|---|
| GET | `/releases/latest` | 公开 | 最新正式版本 |
| GET | `/releases/check` | 公开 | 灰度/强制更新检查 |
| GET | `/health` | 公开 | 服务健康检查 |

## Safe+Write 自动化覆盖

脚本覆盖：TLS/安全响应头、CORS、JWT 缺失/畸形、普通用户访问管理端、公开/受保护路由矩阵、异常方法、分页边界、SQL 注入型输入、文件路径穿越、双账号身份隔离、文件上传下载及跨账号访问、报告写入及跨账号删除、评论写入及跨账号删除、推送订阅写入与清理，以及只读接口压测。

脚本明确排除：短信、真实验证码验证、微信登录/绑定、密码修改、账号注销/恢复、云数据覆盖/清空、AI 生成/识别、订单创建、退款、有效支付回调、消息已读和阅读埋点。排除原因是会产生费用、不可逆副作用或无法可靠恢复原状态。
