# 🐾 Wangzai AI Agent · 旺仔智能桌宠

<p align="center">
  <strong>让大模型从「回答问题」走向「调用工具、感知环境、控制真实设备」</strong>
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white">
  <img alt="Spring Boot 4.1.1" src="https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white">
  <img alt="Vue 3" src="https://img.shields.io/badge/Vue-3-42B883?logo=vuedotjs&logoColor=white">
  <img alt="DeepSeek API" src="https://img.shields.io/badge/LLM-DeepSeek%20API-2563EB">
  <img alt="ESP32-S3" src="https://img.shields.io/badge/Hardware-ESP32--S3-00979D">
  <img alt="SQLite" src="https://img.shields.io/badge/Memory-SQLite-003B57?logo=sqlite&logoColor=white">
</p>

> **项目定位：Java AI Agent 应用开发 + AIoT 设备联动。** 该项目通过 DeepSeek 的工具调用机制，让 AI 决定何时查询设备、获取天气和控制桌宠表情；Java 后端负责执行实际工具、校验结果、维护会话与记录执行轨迹。
>
> **当前阶段：本地开发和作品演示。** 已有可运行的工具链路及前端控制台；不代表已完成公开云部署、通用 Agent 平台或工业级设备控制认证。

## 📖 目录

- [项目简介](#-项目简介)
- [功能清单](#-功能清单)
- [系统架构](#-系统架构)
- [多轮 Tool Calling 工作流程](#-多轮-tool-calling-工作流程)
- [技术栈](#-技术栈)
- [项目目录](#-项目目录)
- [快速开始（MOCK 模式）](#-快速开始mock-模式)
- [真实 ESP32-S3 接入](#-真实-esp32-s3-接入)
- [接口说明与请求示例](#-接口说明与请求示例)
- [会话记忆与执行轨迹](#-会话记忆与执行轨迹)
- [测试与验收](#-测试与验收)
- [安全说明与已知限制](#-安全说明与已知限制)
- [后续规划](#-后续规划)
- [项目展示](#-项目展示)

## 💡 项目简介

**旺仔（Wangzai）** 是一个面向桌面实体智能宠物场景的 Java AI Agent。用户可以用自然语言对旺仔说：

- 「旺仔，查询一下你现在的设备状态。」
- 「把表情切换成 sad。」
- 「查询广州现在的天气。」
- 「根据广州的实时天气决定旺仔应该显示什么表情。」

这不只是一个把文本转发给大模型的聊天框。系统将 **语言模型、Java 工具执行、网络服务、SQLite 会话记忆、ESP32 设备和 Vue 可视化控制台** 连接成一个实际的交互闭环。

### 核心设计理念

**模型负责选择动作，应用负责安全执行，设备结果决定最终回复。**

- DeepSeek 根据当前对话和工具定义选择需要执行的 Tool。
- Java 通过 `ToolRegistry` 查找并执行工具，不把设备控制权限直接交给模型。
- 工具执行结果以 `tool` 消息回传模型，使其能够继续决策或形成最终回答。
- 对不确定的设备执行结果、天气查询异常和非法参数进行保护，避免未经确认就宣称操作成功。

## ✨ 功能清单

| 模块 | 当前已实现能力 |
| --- | --- |
| AI 对话 | Spring Boot `/api/chat` 接口接收自然语言；DeepSeek Chat Completions API |
| 多轮工具调用 | 模型选择工具 → Java 执行 → 结果回传 → 模型再次判断或最终回复 |
| 工具注册 | `ToolRegistry` 管理工具名称、Schema 和执行处理器 |
| 表情控制 | `showExpression` 支持 `happy`、`sad`、`sleep` |
| 设备状态 | `getDeviceStatus` 查询在线状态、当前表情、运行时间和可用内存 |
| 天气查询 | `getCurrentWeather` 对接 Open-Meteo 查询指定城市天气 |
| 天气驱动表情 | 在满足天气数据、执行顺序及映射规则时控制表情 |
| 会话记忆 | 指定 `sessionId`，持久化最近 5 轮会话到 SQLite |
| 执行确认 | 可选择基于 `command_id`、`boot_id` 查询指定设备指令执行状态 |
| 安全防护 | 工具调用次数上限、工具参数白名单、天气顺序控制、设备失败不自动重复下发 |
| Agent Trace | JSONL 记录模型轮次、工具执行、耗时等，提供查询接口 |
| Vue 控制台 | 对话输入、会话创建、模拟设备状态、Trace 轨迹展示与 JSONL 导入 |
| 开发模拟 | Python HTTP MOCK 设备，可在没有真实硬件时跑通控制流程 |

> **实现边界**：前端右侧「设备状态」区域当前读取的是 **Python MOCK 服务**（`/mock/api/device/status`）；不是自动切换到真实 ESP32 的可视化面板。真实设备状态可由后端 `getDeviceStatus` Tool 查询。多轮 Agent、设备和天气功能需要相应服务、密钥与网络环境正常运行。

## 🏗️ 系统架构

```mermaid
flowchart TB
    U["用户 / 浏览器"] --> V["Vue 3 + TypeScript 控制台"]
    V --> API["Spring Boot REST API"]
    API --> AUTH["API Token 过滤器"]
    AUTH --> AGENT["ChatService / AgentService"]
    AGENT --> LLM["LlmService / DeepSeek"]
    AGENT --> REG["ToolRegistry"]
    REG --> W["getCurrentWeather"]
    REG --> D["getDeviceStatus"]
    REG --> E["showExpression"]
    W --> METEO["Open-Meteo"]
    D --> DEV["ESP32-S3 / MOCK"]
    E --> DEV
    AGENT --> MEM["ConversationMemory"]
    MEM --> SQL[("SQLite")]
    AGENT --> TRACE["AgentTraceRecorder / JSONL"]
    API --> VIEW["Trace 查询 API"]
    VIEW --> TRACE
    V -. "开发期独立状态面板" .-> MOCK["Python MOCK :18080"]
```

### 主要职责

| 组件 | 作用 |
| --- | --- |
| `ChatController` | 请求参数与 `sessionId` 校验，提供聊天入口 |
| `ChatService` | 包装 Agent 的输出为 HTTP 响应 |
| `AgentService` | 管理多轮推理、工具调用、结果回传及安全规则 |
| `LlmService` | 使用 Java `HttpClient` 请求模型，处理 HTTP 错误和超时 |
| `ToolRegistry` | 注册工具 Schema 和执行器，按名称调度 |
| `ShowExpressionTool` | 校验表情参数，发送设备命令并按配置确认执行结果 |
| `GetDeviceStatusTool` | 获取设备状态，检查设备返回字段 |
| `GetCurrentWeatherTool` | 查询外部天气并校验工具输入 |
| `ConversationMemory` | 按会话组织历史内容；正式 Spring 环境写入 SQLite |
| `AgentTraceRecorder` | 记录执行事件，限制 Trace 中的敏感字段 |
| `AgentTraceController` | 通过已认证接口读取最新的一组执行轨迹 |

## 🔁 多轮 Tool Calling 工作流程

```mermaid
sequenceDiagram
    actor User as 用户
    participant API as Spring Boot
    participant Agent as AgentService
    participant LLM as DeepSeek
    participant Tools as ToolRegistry
    participant Ext as 天气 API / ESP32
    participant DB as SQLite
    User->>API: POST /api/chat + sessionId
    API->>Agent: 进入会话
    Agent->>DB: 读取最近历史（如果有 sessionId）
    Agent->>LLM: messages + tools + tool_choice=auto
    LLM-->>Agent: tool_calls
    loop 最多执行 3 个工具
        Agent->>Tools: 校验与调度工具
        Tools->>Ext: 查询天气 / 读取状态 / 发送表情
        Ext-->>Tools: JSON 执行结果
        Tools-->>Agent: 工具结果
        Agent->>LLM: 回传 tool 消息，继续决策
        LLM-->>Agent: 下一步调用或最终内容
    end
    Agent->>DB: 保存本轮问答（如果有 sessionId）
    Agent-->>API: 最终回复
    API-->>User: reply + status
```

### 当前注册的三个工具

| 工具名 | 参数示例 | 执行效果 |
| --- | --- | --- |
| `showExpression` | `{"action":"happy"}` | 控制表情；允许值仅为 `happy`、`sad`、`sleep` |
| `getDeviceStatus` | `{}` | 读取设备在线状态、表情、运行时间和内存 |
| `getCurrentWeather` | `{"city":"广州"}` | 获取指定城市的当前天气信息 |

### 重要工程细节

- **工具调用预算**：单次聊天至多执行 **3 次工具调用**，防止模型无节制调用外部接口。
- **多轮反馈**：不是一次函数调用结束就返回；Agent 可以接收工具结果后再次请求模型。
- **明确执行状态**：设备返回 `accepted` 只表示已接受命令；`completed` 或状态一致性结果也不能等同于屏幕逐帧物理刷新证明。
- **幂等与不确定性保护**：超时或无法确认时不自动重发设备命令，避免重复执行。
- **天气安全规则**：天气相关请求需要先成功获取天气结果，且遵循独立的天气到表情映射。

天气表情映射（以当前源码为准）：

| 天气分类 | 目标表情 |
| --- | --- |
| `clear`、`partly_cloudy` | `happy` |
| `overcast`、`rainy`、`foggy`、`snowy` | `sad` |
| 未识别天气类别或查询失败 | 阻止自动表情切换 |

## 🧰 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot **4.1.1**、Spring Web MVC、Maven |
| 大模型 | DeepSeek 兼容的 Chat Completions API、Java `HttpClient` |
| 工具框架 | 自定义 `ToolRegistry`、Function Calling Schema、Agent 循环 |
| 设备通信 | HTTP / JSON、ESP32-S3、可选命令 ID 确认 |
| 天气服务 | Open-Meteo |
| 会话记忆 | SQLite JDBC，单个会话最多保留最近 5 轮 |
| 事件追踪 | 本地 JSONL、Trace 查询接口、调用耗时 |
| 前端 | Vue 3、TypeScript、Vite **7** |
| MOCK 测试 | Python 3 标准库 `http.server` |
| 测试 | JUnit、Spring Boot Test、集成测试 |

## 📂 项目目录

```text
wangzai-ai-agent/
├── README.md
├── pom.xml
├── start-wangzai.sh             # Ubuntu 本地启动辅助脚本（包含固定路径）
├── tools/
│   └── mock_esp32.py            # Python 模拟 ESP32 设备
├── src/
│   ├── main/
│   │   ├── java/com/example/ai_pet_agent/
│   │   │   ├── controller/      # Chat、Health、Session、Mock 等接口
│   │   │   ├── service/         # Agent 编排、会话、Trace
│   │   │   ├── llm/             # DeepSeek 通信与示例程序
│   │   │   ├── tool/            # ToolRegistry + 三个工具
│   │   │   ├── device/          # ESP32 HTTP 客户端与指令确认
│   │   │   ├── weather/         # 天气客户端
│   │   │   ├── memory/          # SQLite 会话记忆
│   │   │   ├── security/        # API Token 过滤器
│   │   │   └── dto/             # API 请求与响应类型
│   │   └── resources/application.properties
│   └── test/                    # 单元与 HTTP 集成测试
└── frontend/
    ├── src/App.vue              # Agent 对话 / Trace / MOCK 设备面板
    ├── src/main.ts
    ├── vite.config.ts           # 代理 /api 和 /mock
    └── package.json
```

## ⚡ 快速开始（MOCK 模式）

**推荐先运行 MOCK 模式。** 不需要 ESP32 硬件，就能体验 Agent Tool Calling、状态查询和表情切换。

### 1. 环境要求

- **JDK 21**
- Maven（或使用仓库自带的 Maven Wrapper）
- Node.js（满足 `frontend/package.json` 中 Vite / TypeScript 的版本要求）及 npm
- Python 3（标准库即可）
- 可访问的 DeepSeek API 地址及有效 API Key

Ubuntu 已验证过 Java 21、Maven、Spring Boot 和 Vue 开发流程；具体依赖以 `pom.xml`、`frontend/package-lock.json` 为准。

### 2. 克隆仓库

```bash
git clone https://github.com/211irving123456-del/wangzai-ai-agent.git
cd wangzai-ai-agent
```

### 3. 启动 Python 模拟设备

在终端 A 中：

```bash
python3 tools/mock_esp32.py
```

默认监听 **`127.0.0.1:18080`**，支持：

- `POST /mock-esp32/expression`：接受 `happy` / `sad` / `sleep`
- `GET /api/device/status`：获取模拟设备状态
- `GET /api/device/command?id=...`：查询特定命令结果
- `GET /health`：模拟设备健康检查

可选检查：

```bash
curl http://127.0.0.1:18080/health
```

### 4. 配置后端环境变量

在**新的终端 B**、仓库根目录执行。输入真实 API Key 时请勿将它发布到 GitHub、截图或终端日志。

```bash
# 交互输入访问令牌与大模型密钥
read -r -s -p "Wangzai API Token: " WANGZAI_AUTH_TOKEN; echo
read -r -s -p "DeepSeek API Key: " LLM_API_KEY; echo

export WANGZAI_AUTH_TOKEN LLM_API_KEY
export LLM_API_URL="https://api.deepseek.com/chat/completions"

# 请根据自己所使用服务端实际提供的模型 ID 设置；这里是项目脚本里的默认值
export LLM_MODEL="deepseek-flash"

# MOCK 设备端点及确认机制
export ESP32_DEVICE_URL="http://127.0.0.1:18080/mock-esp32/expression"
export ESP32_DEVICE_TOKEN="mock-local-token"
export ESP32_CONFIRM_BY_ID=true

# 本地执行追踪
export WANGZAI_TRACE_ENABLED=true

mvn spring-boot:run
```

> **当前源码的一个重要配置限制**：`src/main/resources/application.properties` 有一行 `wangzai.auth.token=wangzai-test-token`，覆盖前一行环境变量占位符。因此，**直接通过上述环境变量运行时，`/api/chat` 与 `/api/sessions/**` 的鉴权 Token 可能与环境变量不一致**；Trace 查询又直接读取 `WANGZAI_AUTH_TOKEN`。为避免误解，请先在自己的本地开发副本中删除那行硬编码的 `wangzai.auth.token=wangzai-test-token`（测试资源 `src/test/resources/application.properties` 中的测试 Token 可保留用于测试），再按上述环境变量启动。**这项安全配置仍需提交独立代码修复，不应把现状视为适合公开部署。**
>
> 另外，`start-wangzai.sh` 中 `PROJECT_DIR` 固定为 `$HOME/Project/ai-pet-agent/ai_pet_agent`，不能在任意克隆目录中直接运行。为了通用性，本 README 的快速开始优先使用上面的 `mvn spring-boot:run`。原脚本会从 `~/.config/wangzai/` 加载 Token 和 Key，且默认启用命令 ID 确认；如果要使用，请确认本机路径和密钥文件配置正确。

启动成功后：

- 后端默认地址：**<http://127.0.0.1:8080>**
- 健康接口：**<http://127.0.0.1:8080/api/health>**

### 5. 启动 Vue 控制台

在**新的终端 C**：

```bash
cd frontend
npm ci
npm run dev
```

浏览器访问 **<http://127.0.0.1:5173/>**。

前端 Vite 开发代理：

- `/api/*` → `http://127.0.0.1:8080`（Spring Boot）
- `/mock/*` → `http://127.0.0.1:18080`（Python 模拟设备）

在控制台填写本地 API Token，发送任务即可。Token 当前只保留在页面内存中，不写入浏览器 `localStorage`。

**注意**：该控制台定位于本机开发，Vite 服务监听 `127.0.0.1:5173`。如果在 Windows 宿主机访问 VMware 中的 Ubuntu 服务，需要另行配置监听地址、代理目标与网络访问限制；默认配置并非跨机器部署方案。

### 6. 推荐体验命令

```text
旺仔，查询一下你现在的设备状态。
旺仔，把表情切换成 sad。
旺仔，请查询广州当前天气。
旺仔，请根据广州当前天气决定应该显示什么表情。
```

使用天气相关功能前，确保能够访问 Open-Meteo；若天气数据获取失败，Agent 应返回失败信息，而不是假装查询成功或擅自控制设备。

## 🔌 真实 ESP32-S3 接入

项目提供独立设备客户端，支持将表情控制发送到 ESP32-S3。真实硬件需运行与当前客户端协议匹配的固件。

### 通信协议（Java 客户端期望）

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/mock-esp32/expression` | 设置表情，JSON 参数 `{"action":"sad"}` |
| `GET` | `/api/device/status` | 读取设备在线状态、表情、运行时间、可用内存 |
| `GET` | `/api/device/command?id=123` | 查询指定表情控制指令是否执行完毕 |

使用指令 ID 确认时，Java 端要求：

1. POST 响应包含 `success`、`command_id`、`boot_id` 等字段。
2. 按命令 ID 轮询 `/api/device/command`，检查命令 ID、动作与 `boot_id` 是否一致。
3. 设备返回 `completed` 时，才把该命令视为已按协议确认；超时或异常情况下不自动重复下发。

实际项目中可以这样设置设备地址（**示例 IP，不代表真实硬件地址**）：

```bash
export ESP32_DEVICE_URL="http://192.168.1.50/mock-esp32/expression"
export ESP32_DEVICE_TOKEN="YOUR_DEVICE_TOKEN"
export ESP32_CONFIRM_BY_ID=true
```

对于真实设备：

- PC / VMware 与 ESP32 必须网络互通。
- 固件需要实现相应 JSON 接口及 Bearer Token 校验。
- `accepted`、`completed` 与「屏幕像素确实刷新」是不同的证明层级，不混为一谈。
- 如果固件只支持旧版返回结构，需先核对 `ESP32_CONFIRM_BY_ID` 配置与协议兼容性。

**ESP32-S3 + GC9A01 圆形屏** 是项目目标硬件场景；本仓库主体是 Java 后端、Web 控制台和 MOCK 服务，**不包含完整的 ESP32 固件工程**，因此无法仅靠克隆仓库直接烧录硬件。

## 🌐 接口说明与请求示例

| 模块 | HTTP | 路径 | 说明 |
| --- | --- | --- | --- |
| 健康检查 | GET | `/api/health` | 返回后端健康状态 |
| Agent | POST | `/api/chat` | 无状态消息 / 指定会话的多轮 Agent 请求 |
| 会话 | GET | `/api/sessions/{sessionId}/history` | 获取会话最近历史 |
| 会话 | DELETE | `/api/sessions/{sessionId}/history` | 清除指定会话历史 |
| 执行轨迹 | GET | `/api/agent/traces/latest` | 返回最新 Trace 的事件 |
| 本机调试 | POST | `/mock-esp32/expression` | Spring Boot 内部的简单模拟控制接口 |

### Agent 聊天

```bash
curl -X POST "http://127.0.0.1:8080/api/chat" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_WANGZAI_API_TOKEN" \
  -d '{
    "sessionId": "demo-001",
    "message": "旺仔，查询当前设备状态，然后告诉我你现在的表情"
  }'
```

`sessionId` 为可选字段：省略时沿用无持久化会话的聊天路径；提供时需要符合 **1–64 位字母、数字、下划线或连字符** 的格式。

响应结构示例（仅展示字段，不代表实际模型回复）：

```json
{
  "reply": "这里是 Agent 的最终回复",
  "status": "success"
}
```

### 查询会话历史

```bash
curl "http://127.0.0.1:8080/api/sessions/demo-001/history" \
  -H "Authorization: Bearer YOUR_WANGZAI_API_TOKEN"
```

清空指定会话历史：

```bash
curl -X DELETE "http://127.0.0.1:8080/api/sessions/demo-001/history" \
  -H "Authorization: Bearer YOUR_WANGZAI_API_TOKEN"
```

### 查询最新 Agent Trace

```bash
curl "http://127.0.0.1:8080/api/agent/traces/latest" \
  -H "Authorization: Bearer YOUR_WANGZAI_API_TOKEN"
```

当 `WANGZAI_TRACE_ENABLED=true` 时，默认写入 `data/agent-traces.jsonl`。API 返回最近一组 `traceId` 对应的有限事件；前端还支持导入本地 JSONL 文件进行查看。

## 🧠 会话记忆与执行轨迹

### SQLite 会话记忆

- 运行时默认数据库路径：`data/wangzai.db`。
- Spring 环境使用 `SqliteChatRepository` 自动创建 `conversation_turns` 表。
- 每个 `sessionId` 最多保留最近 **5 轮** 用户消息与 Agent 回复。
- 保存和裁剪旧会话记录在 SQLite 事务中执行。
- 会话历史可以通过 REST 接口读取或删除。
- 单元测试中还支持不依赖数据库的内存实现。

### Trace 可观测性

- 每次 Agent 执行生成独立的 `trace_id`。
- 记录 `start`、`llm_decision`、`tool` 事件，以及执行轮次和耗时。
- 对部分工具参数和结果采用字段白名单，避免直接记录完整聊天原文。
- JSONL 记录功能默认关闭，需要显式启用。
- `/api/agent/traces/latest` 接口提供最近一组追踪记录；Vue 控制台支持刷新与文件导入。

> Trace 不是完整的分布式链路追踪平台；目前主要用于本地学习、调试和演示。敏感设备信息仍应按实际部署环境审查。

## ✅ 测试与验收

### 后端测试

在仓库根目录：

```bash
mvn test
```

仓库包含针对以下场景的 JUnit / Spring 测试：

- Agent 多轮 Tool Calling 和工具编排
- ToolRegistry 注册、Schema 和执行
- 会话记忆、SQLite 持久化、历史管理
- API Token 鉴权及 HTTP 错误处理
- ESP32 客户端、设备确认和失败保护
- 天气规则、工具失败场景及工具执行顺序
- Agent Trace 记录和查询相关行为

项目此前 README 记录的本地验收结果为 **Java 测试 60 项通过、Vue 构建成功、真机和 MOCK 链路已验证**。这些属于**既有记录**，本次 README 更新未重新执行测试。请以自己最新运行的 `mvn test` 结果为准，不把该数字当作自动更新的 CI 徽章。

### 前端构建

```bash
cd frontend
npm ci
npm run build
```

该命令包含 TypeScript 检查（`vue-tsc`）和 Vite 生产构建。当前仓库尚未提供 GitHub Actions CI 或公开部署地址。

### 建议手工验收

1. 启动 Python MOCK、Spring Boot、Vue 三个独立进程。
2. 测试正确 / 错误 API Token，确认未授权请求被阻止。
3. 测试 `getDeviceStatus`、`showExpression` 和 `getCurrentWeather`。
4. 确认多轮工具调用过程中模型读到了前一步结果。
5. 使用相同 `sessionId` 连续对话，验证历史持久化；重启后端再次读取。
6. 查看最新 Agent Trace，核对工具名称、轮次和状态。
7. 如接入真实 ESP32，单独确认命令回执、设备状态和实际屏幕表现。

## 🔐 安全说明与已知限制

本项目实现了一些初步安全边界，但还需要进一步加固：

- **高优先级配置问题**：主配置 `application.properties` 中存在硬编码测试 Token 覆盖环境变量的情况，需在正式合并安全修复前处理。任何已有公开测试 Token 都不能用作真实设备或生产访问凭据。
- **不要提交敏感信息**：DeepSeek API Key、`WANGZAI_AUTH_TOKEN`、`ESP32_DEVICE_TOKEN`、SQLite 数据文件、Trace JSONL 日志均不应上传。
- **服务暴露面**：`/api/chat`、会话接口有 Token 过滤器保护，Trace 接口有独立鉴权；其他本地调试接口并非统一受到相同保护，不建议直接暴露公网。
- **请求与响应可靠性**：设备命令的「接收」「执行状态」「真实屏幕刷新」必须区分，不应凭 HTTP 200 推断设备已完成物理动作。
- **天气边界**：Open-Meteo 结果依赖网络与外部服务，错误或缺失数据时不应控制表情。
- **启动脚本可移植性**：`start-wangzai.sh` 包含个人机器路径，迁移到另一台计算机需要修改或改用通用手动启动。
- **Web 控制台**：定位本机开发环境；不能直接当作面向公众的多用户控制平台。
- **硬件工程**：当前公开仓库未包含完整的 ESP32-S3 固件，可运行的模拟器不等同于可直接烧录的硬件项目。

## 🛣️ 后续规划

### 工程加固（优先）

- 移除主配置里的硬编码 Token，并为生产配置添加启动前校验。
- 重构硬编码启动目录，让脚本在任意克隆位置均可运行。
- 增加 CI、依赖安全扫描、端到端回归测试。
- 完善设备授权、命令确认、超时与会话隔离。
- 提供经过真实设备验证的硬件接口文档和固件仓库链接。

### Agent 能力进阶

- 扩展 Tool Registry 工具种类与参数校验。
- 加入长期记忆 / 更精细的上下文管理。
- 增强 Agent 可观测性、失败分析与性能统计。
- 探索 RAG、任务计划器与更复杂的多步骤设备任务。

## 📷 项目展示

**目前 README 不放虚构的项目截图或在线视频。** 建议后续补充真实图片（对 Token、IP、个人信息打码）：

- Vue 控制台整体界面
- Agent 多轮工具调用与 Trace 执行轨迹
- Python MOCK 模式表情切换
- 真实 ESP32-S3 + 圆形屏幕桌宠照片
- 天气查询及规则驱动表情的完整演示

可以将截图保存到 `docs/images/` 并在此处插入对应图片，再添加 30–60 秒的真实操作演示视频链接。

---

### 🌱 项目价值

**Wangzai AI Agent** 用一个桌宠的实际场景串联 AI Agent 开发中的关键工程问题：模型怎样选择工具、后端怎样约束权限、设备结果怎样影响下一步决策、失败时怎样避免错误操作，以及执行过程怎样让开发者看得见。

它是一个持续迭代的 Java AI Agent + AIoT 实践项目，适合作为 AI 应用开发 / AI Agent 开发方向的技术学习与作品展示基础。
