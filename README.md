# AI Gateway

一个基于 Java 17 与 Spring WebFlux 构建的企业级 AI 网关。它向客户端提供统一的
OpenAI-compatible API，并在多个大模型供应商之间完成模型映射、故障回退、鉴权、限流和可观测性治理。

本项目不仅演示“调用大模型”，更关注网关在真实生产环境中需要解决的稳定性、扩展性和治理问题。

## 核心能力

- 提供 OpenAI-compatible `POST /v1/chat/completions`
- 通过 `GET /v1/models` 查询网关对外暴露的逻辑模型
- 使用逻辑模型屏蔽不同供应商的真实模型名称
- 主供应商超时、连接失败或返回 5xx 时自动切换备用供应商
- 基于 Spring WebFlux 非阻塞透传普通响应和 SSE 流式响应
- 支持 Bearer Token 与 `x-api-key` 两种客户端鉴权方式
- 按 API Key 执行请求频率限制
- 自动生成并返回 `x-request-id`
- 暴露健康检查与 Prometheus 指标
- 提供自动化测试、Docker Compose 和 GitHub Actions CI

## 架构概览

```text
客户端
  |
  | OpenAI-compatible API
  v
RequestIdFilter -> ApiKeyFilter -> RateLimitFilter
  |
  v
ModelRouter（逻辑模型 -> 按优先级排列的供应商）
  |
  v
UpstreamClient（模型改写、流式转发、指标记录）
  |
  +--> 主供应商
  |
  +--> 备用供应商
```

客户端只需要请求稳定的逻辑模型，例如 `smart-chat`。网关会将它映射为不同供应商的真实模型，
因此切换供应商或升级模型时不需要修改客户端。

## 快速开始

### 环境要求

- Java 17
- 项目已经包含 Maven Wrapper，无需单独安装 Maven
- Docker 可选，仅在使用 Docker Compose 时需要

### 启动项目

```powershell
Copy-Item .env.example .env
.\mvnw.cmd spring-boot:run
```

默认情况下，网关会先尝试 OpenAI，再尝试位于 `http://localhost:11434/v1` 的
OpenAI-compatible 服务。你可以在 `.env` 中配置真实的供应商地址和密钥。

### 查询可用模型

```powershell
Invoke-RestMethod `
  -Uri http://localhost:8080/v1/models `
  -Headers @{ Authorization = "Bearer gateway-dev-key" }
```

### 发起聊天请求

```powershell
$body = @{
  model = "smart-chat"
  messages = @(
    @{ role = "user"; content = "请简要解释 Java 虚拟线程" }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod `
  -Uri http://localhost:8080/v1/chat/completions `
  -Method Post `
  -Headers @{ Authorization = "Bearer gateway-dev-key" } `
  -ContentType "application/json" `
  -Body $body
```

## 配置说明

| 环境变量 | 用途 | 默认值 |
| --- | --- | --- |
| `GATEWAY_API_KEYS` | 客户端可使用的 API Key，多个值使用逗号分隔 | `gateway-dev-key` |
| `GATEWAY_REQUESTS_PER_MINUTE` | 每个客户端 Key 每分钟允许的请求数 | `60` |
| `GATEWAY_UPSTREAM_TIMEOUT` | 上游供应商响应超时时间 | `60s` |
| `OPENAI_API_KEY` | OpenAI 上游密钥 | 空 |
| `OPENAI_BASE_URL` | OpenAI-compatible 主供应商地址 | `https://api.openai.com/v1` |
| `OPENAI_MODEL` | 主供应商真实模型 | `gpt-4o-mini` |
| `COMPATIBLE_BASE_URL` | OpenAI-compatible 备用供应商地址 | `http://localhost:11434/v1` |
| `COMPATIBLE_API_KEY` | 备用供应商密钥 | `local-development-key` |
| `COMPATIBLE_MODEL` | 备用供应商真实模型 | `qwen2.5:7b` |

不要将 `.env` 或真实供应商密钥提交到 Git 仓库。

## 路由与故障回退

路由在 `src/main/resources/application.yml` 中声明：

```yaml
gateway:
  routes:
    smart-chat:
      - openai
      - compatible
```

供应商列表顺序即调用优先级。发生连接失败、超时或上游 5xx 时，网关会尝试下一个供应商。
上游 4xx 通常表示请求参数或权限存在问题，因此会直接返回客户端，不触发无意义的回退调用。

## 测试与构建

```powershell
# 执行完整测试和构建校验
.\mvnw.cmd clean verify

# 生成可执行 Jar
.\mvnw.cmd package

# 启动生成的 Jar
java -jar target\ai-gateway-0.1.0-SNAPSHOT.jar
```

当前测试覆盖：

- Spring Boot 上下文和健康检查
- API Key 鉴权
- 逻辑模型查询
- 供应商路由顺序
- 供应商故障自动回退
- 供应商模型名称改写
- 上游 4xx 原样返回
- SSE 流式响应透传

## Docker 与可观测性

```powershell
Copy-Item .env.example .env
.\mvnw.cmd package
docker compose up --build
```

可用地址：

- 健康检查：`http://localhost:8080/actuator/health`
- Prometheus 指标：`http://localhost:8080/actuator/prometheus`
- Prometheus 控制台：`http://localhost:9090`

## 当前设计边界

- 限流采用单机内存固定窗口，多个网关实例之间暂不共享计数
- 供应商配置来自配置文件，暂未提供动态管理后台
- 当前仅对接 Chat Completions API
- 尚未持久化租户、调用记录、Token 用量和成本数据

这些边界为后续演进预留了清晰方向，并明确了当前版本的适用范围。

## 后续规划

- 使用 Redis 实现分布式限流、租户配额和并发控制
- 引入熔断器、供应商健康评分和动态路由
- 记录 Token 用量、调用成本和租户账单
- 使用 MySQL 管理租户、密钥、供应商和审计记录
- 支持敏感信息脱敏、语义缓存和管理控制台
- 接入 OpenTelemetry，并补充 Kubernetes 部署清单

更详细的路由与失败处理设计见 [架构文档](docs/architecture.md)。
