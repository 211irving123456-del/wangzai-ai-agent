# Wangzai AI Agent | 旺仔智能桌宠

基于 Java 21、Spring Boot、DeepSeek API 和 ESP32-S3 开发的智能实体 AI Agent。

本项目重点展示多轮 Tool Calling、Agent 工具编排、设备控制、执行确认与可观测性。

## 核心功能

- 多轮 Tool Calling：模型自主选择工具，执行结果回传后继续决策
- Tool Registry：统一管理工具 Schema 与执行逻辑
- 真实设备控制：支持 ESP32-S3 表情切换及指令确认
- 外部信息获取：集成 Open-Meteo 天气查询
- Agent Memory：SQLite 会话持久化
- 安全机制：工具调用限制、天气控制校验、异常保护
- Agent Trace：记录模型轮次、工具调用与执行耗时
- REAL / MOCK：支持真实 ESP32 和无硬件模拟运行
- Vue 控制台：可视化展示对话、工具轨迹和设备状态

## 技术栈

| 模块 | 技术 |
|---|---|
| 后端 | Java 21 / Spring Boot / Maven |
| 大模型 | DeepSeek API |
| Agent | Multi-turn Tool Calling / Tool Registry |
| 数据存储 | SQLite |
| 前端 | Vue 3 / TypeScript / Vite 7 |
| 设备 | ESP32-S3 |
| 模拟设备 | Python HTTP Server |
| 可观测性 | JSONL Agent Trace |

## 系统架构

用户通过 Vue 控制台向 Spring Boot 发送任务。

Spring Boot 中的 AgentService 调用 DeepSeek API，由模型选择 Tool Registry 注册的工具。

目前支持三个主要工具：

1. getCurrentWeather：查询真实天气
2. getDeviceStatus：获取设备状态
3. showExpression：控制设备表情

工具执行后，结果返回给模型，继续进行下一轮决策。

会话历史使用 SQLite 保存，执行轨迹使用 JSONL 记录。

设备层支持真实 ESP32-S3 和 Python MOCK 两种运行模式。

## 本地运行

### 环境要求

- Java 21
- Maven
- Node.js 22
- Python 3
- 有效的 DeepSeek API Key

### 1. 配置密钥

启动脚本使用用户目录下的配置文件：

- ~/.config/wangzai/api-token
- ~/.config/wangzai/device-token
- ~/.config/wangzai/llm-api-key

这些文件包含敏感信息，不应提交到 GitHub。

### 2. 启动模拟 ESP32

在项目根目录执行：

python3 tools/mock_esp32.py

模拟服务默认监听 127.0.0.1:18080。

### 3. 启动 Spring Boot

在另一个终端执行：

WANGZAI_TRACE_ENABLED=true ESP32_DEVICE_URL="http://127.0.0.1:18080/mock-esp32/expression" ./start-wangzai.sh

Spring Boot 默认运行在 8080 端口。

### 4. 启动 Vue 前端

进入 frontend 目录，执行：

npm ci

npm run dev

浏览器访问 http://127.0.0.1:5173/

输入本地 API Token 后即可使用。

## 示例任务

- 查询当前旺仔的设备状态
- 将旺仔的表情切换为 sad
- 查询广州当前天气
- 根据实时天气调整旺仔的表情

## 自动化测试

后端：mvn test

前端：cd frontend && npm ci && npm run build

最近一次本地验证：

- Java 自动化测试：60 项通过
- Vue 生产构建：成功
- ESP32 真机控制：已验证
- MOCK 模拟设备：已验证
- Agent Trace 查询 API：已验证

## 安全与使用限制

- 不提交 API Key、Token、数据库或运行日志
- 模拟设备默认仅监听本机
- Agent Trace 当前主要用于本地单用户调试
- 设备 completed 状态不代表屏幕物理刷新已被逐帧验证
- 本项目当前为开发及求职展示版本

## 项目定位

本项目专注 AI Agent 工程能力，重点展示大模型工具调用、多轮决策、外部系统集成、设备执行确认和安全控制，而非普通聊天应用。
