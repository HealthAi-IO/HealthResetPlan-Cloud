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
| `AI_CHAT_DEEPSEEK_API_KEY` / `AI_CHAT_DOUBAO_API_KEY` / `AI_CHAT_QWEN_API_KEY` | AI 厂商 Key |
| `AI_CHAT_DEEPSEEK_API_BASE` / `AI_CHAT_DOUBAO_API_BASE` / `AI_CHAT_QWEN_API_BASE` | AI 厂商 OpenAI 兼容接口地址 |
| `AI_CHAT_DEEPSEEK_MODEL` / `AI_CHAT_DOUBAO_MODEL` / `AI_CHAT_QWEN3_VL_PLUS_MODEL` | AI 模型名称 |
| `AI_CHAT_DEEPSEEK_WEB_SEARCH_MODEL` | DeepSeek 联网搜索 Bot 应用 ID |

## 配置位置

AI 配置位于：

- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`

后端代码通过 `app.ai.providers.*` 读取这些配置。
