# HealthResetPlan-Cloud

> 健康重启计划后端 API：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL 8 + Redis 7。

## 工程结构

```
HealthResetPlan-Cloud/
 ├── pom.xml
 ├── sql/
 │    └── migration/            # Flyway DDL 脚本
 ├── src/main/java/io/healthresetplan/
 │    ├── HealthResetPlanApplication.java
 │    ├── common/                # 通用工具（R / 异常 / 健康检查）
 │    ├── config/                # Bean 配置（Security 等）
 │    └── modules/
 │         ├── ai/               # 大模型适配（DeepSeek / 豆包 / 通义千问）
 │         ├── sync/             # 端到端加密同步
 │         └── user/             # 用户
 ├── src/main/resources/
 │    ├── application.yml
 │    ├── application-dev.yml
 │    ├── application-prod.yml
 │    └── prompts/               # 提示词模板
 └── scripts/                    # 部署脚本（待补充）
```

## 关键设计

- **端到端加密**：服务端不持有用户主密钥；所有 `cipher / iv / tag` 三元组字段服务端不解密。
- **AI 适配**：抽象 `LlmClient` 接口，按厂商实现 OpenAI 兼容协议。
- **Flyway 迁移**：DDL 集中在 `sql/migration/`，启动时自动执行。
- **接口前缀**：`/api/v1`。

## 本地开发

```bash
# 1. 准备 MySQL 与 Redis（默认 localhost）
# 2. 创建库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS health_reset_plan DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 3. 编译 & 运行
mvn -q -DskipTests compile
mvn spring-boot:run
```

环境变量（开发可省略）：

| 变量 | 说明 |
| --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 凭据 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 主机 |
| `JWT_SECRET` | JWT 签名密钥 |
| `DEEPSEEK_API_KEY` / `DOUBAO_API_KEY` / `QWEN_API_KEY` | AI 厂商 Key |

Swagger UI：<http://localhost:8080/swagger-ui.html>。

## 文档

- 完整需求 / 架构 / 数据库 / 接口文档：[`/docs`](../docs)
- 端到端加密方案：[`/docs/05-安全与加密`](../docs/05-安全与加密)
- AI 集成：[`/docs/06-AI模型集成`](../docs/06-AI模型集成)

## 许可证

Apache License 2.0
