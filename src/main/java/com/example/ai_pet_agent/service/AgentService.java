package com.example.ai_pet_agent.service;
import com.example.ai_pet_agent.memory.ConversationMemory;
import com.example.ai_pet_agent.device.Esp32Client;
import com.example.ai_pet_agent.device.DeviceStatusClient;
import com.example.ai_pet_agent.llm.LlmService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.example.ai_pet_agent.device.CommandConfirmationClient;
import org.springframework.stereotype.Service;
import com.example.ai_pet_agent.tool.ToolRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.example.ai_pet_agent.tool.ShowExpressionTool;
import com.example.ai_pet_agent.tool.GetDeviceStatusTool;
import com.example.ai_pet_agent.tool.GetCurrentWeatherTool;
@Service
public class AgentService {
    private static final Logger log =
        LoggerFactory.getLogger(AgentService.class);
    private final LlmService llmService;
    private final Esp32Client esp32Client;
    private final JsonMapper mapper;
    private final ConversationMemory memory;
    private static final Set<String> ALLOWED_ACTIONS =
            Set.of("happy", "sad", "sleep");

    private static final int MAX_TOOL_CALLS = 3;
    private final ToolRegistry toolRegistry;
@Autowired
public AgentService(
        LlmService llmService,
        ConversationMemory memory) {

    this(
            llmService,

                new Esp32Client(
                  System.getenv().getOrDefault(
                     "ESP32_DEVICE_URL",
                   "http://localhost:8080/mock-esp32/expression"
                )
                ),
            memory
    );
}

// 保留旧的双参数构造方法，避免之前的单元测试失效
AgentService(
        LlmService llmService,
        Esp32Client esp32Client) {

    this(
            llmService,
            esp32Client,
            new ConversationMemory()
    );
}

// 允许测试代码注入指定的 ConversationMemory
AgentService(
        LlmService llmService,
        Esp32Client esp32Client,
        ConversationMemory memory) {
    this(llmService, esp32Client, memory, new GetCurrentWeatherTool());
}

// LESSON33_WEATHER_FAILURE_GUARD
// 允许单元测试注入模拟天气工具
AgentService(
        LlmService llmService,
        Esp32Client esp32Client,
        ConversationMemory memory,
        GetCurrentWeatherTool weatherTool) {

    this.llmService = llmService;
    this.esp32Client = esp32Client;
    this.memory = memory;
    this.mapper = JsonMapper.builder().build();


this.toolRegistry = new ToolRegistry()

        .register(
            "showExpression",
            ShowExpressionTool.schema(),
            new ShowExpressionTool(esp32Client)::execute
        )

        .register(
            "getDeviceStatus",
            GetDeviceStatusTool.schema(),
            new GetDeviceStatusTool()::execute
        )
        .register(
            "getCurrentWeather",
            GetCurrentWeatherTool.schema(),
            weatherTool::execute
        );


}


// 旧接口：保持原有行为，不读取或保存会话历史
public String chat(String userMessage) {
    return chatInternal(null, userMessage);
}

// 新接口：支持指定会话的多轮聊天
public String chat(String sessionId, String userMessage) {

    if (sessionId == null || sessionId.isBlank()) {
        throw new IllegalArgumentException(
                "sessionId 不能为空"
        );
    }

    return chatInternal(sessionId, userMessage);
}

private String chatInternal(
        String sessionId,
        String userMessage) {

    // 1. 创建消息列表（只声明一次）
    List<Map<String, Object>> messages =
            new ArrayList<>();

    // 2. 添加系统提示词
    messages.add(Map.of(
            "role", "system",
            "content",
            "你是AI桌宠旺仔。可以友好地聊天。" +
            "需要控制表情时，可以调用showExpression工具。" +
            "当用户询问当前表情、在线状态、运行时间或内存时，必须调用getDeviceStatus查询真实设备，不要根据聊天历史猜测。" +
            "showExpression返回accepted只表示设备接收了指令，不代表显示已更新。" +
            "当showExpression工具结果的status为state_observed时，代表设备当前记录的表情与目标一致，" +
            "但并非逐帧硬件刷新证明；当status为unconfirmed时不要说已经切换成功。" +
            "必须根据工具实际执行结果回答用户，工具失败时不能声称已经成功。" +
            "当任务需要多个步骤时，可以先调用工具获取信息，" +
            "再根据工具返回结果选择下一步工具。" +
            "例如，先调用getDeviceStatus，" +
            "再根据当前表情决定是否调用showExpression。" +
            "所有设备执行结果必须以工具返回为准。" +
            "如果设备请求超时、执行失败或无法确认，" +
            "不要自动重复发送设备控制命令。" +
            "天气查询失败或天气不符合用户指定条件时，不得擅自切换设备表情。"

    ));

    // 3. 如果存在 sessionId，读取历史对话
    if (sessionId != null) {

        List<ConversationMemory.Turn> history =
                memory.getHistory(sessionId);

        for (ConversationMemory.Turn turn : history) {

            messages.add(Map.of(
                    "role", "user",
                    "content", turn.userMessage()
            ));

            messages.add(Map.of(
                    "role", "assistant",
                    "content", turn.assistantReply()
            ));
        }
    }


    // 4. 添加当前用户消息
    messages.add(Map.of(
            "role", "user",
            "content", userMessage
    ));

    // 5. 执行多轮 Tool Calling
    String finalReply = runToolCallingLoop(messages, userMessage);

    // 6. 保存会话记忆
    if (sessionId != null) {
        memory.saveTurn(
                sessionId,
                userMessage,
                finalReply
        );
    }

    return finalReply;

} // chatInternal 方法在这里结束


/**
 * Lesson 32:
 * Multi-round Tool Calling
 *
 * Supports:
 * 1. Multiple model decisions
 * 2. Tool result feedback
 * 3. Total tool-call limit
 * 4. Safe handling of uncertain device operations
 */
private String runToolCallingLoop(
        List<Map<String, Object>> messages,
        String userMessage
) {

    // LESSON34_AGENT_TRACE
    String traceId = java.util.UUID.randomUUID().toString();
    log.info("Agent Trace ID: {}", traceId);
    AgentTraceRecorder.start(traceId);

    int executedTools = 0;
    boolean blockFurtherExpressions = false;
    boolean weatherQueryFailed = false;

    // LESSON33_WEATHER_ORDER_GUARD
    boolean weatherDependent =
            isWeatherDependentRequest(userMessage);
    int successfulWeatherRound = -1;
    String successfulWeatherCondition = null;

    // 最多允许3次工具执行，再进行一次最终回答
    for (int round = 0; round <= MAX_TOOL_CALLS; round++) {

        Map<String, Object> request =
                new LinkedHashMap<>();

        request.put("messages", messages);
        request.put("thinking", Map.of("type", "disabled"));
        request.put("stream", false);

        // 仍有工具预算时，允许模型继续选择工具
        if (executedTools < MAX_TOOL_CALLS) {

            request.put(
                    "tools",
                    toolRegistry.schemas()
            );

            request.put("tool_choice", "auto");
        }

        log.info(
                "LLM决策轮次={}, 已执行工具数={}",
                round + 1,
                executedTools
        );

        long llmStartNs = System.nanoTime();

        JsonNode root = llmService.complete(request);

        AgentTraceRecorder.model(
                traceId,
                round + 1,
                (System.nanoTime() - llmStartNs) / 1_000_000
        );

        JsonNode assistant = root
                .path("choices")
                .path(0)
                .path("message");

        if (!assistant.isObject()) {
            throw new IllegalStateException(
                    "模型返回的message格式不正确"
            );
        }

        JsonNode toolCalls =
                assistant.path("tool_calls");

        // 情况一：没有新的工具调用，返回最终回复
        if (!toolCalls.isArray()
                || toolCalls.size() == 0) {

            String reply =
                    assistant.path("content").asText();

            if (reply.isBlank()) {
                throw new IllegalStateException(
                        "模型没有返回聊天内容"
                );
            }

            return reply;
        }

        // 已经不允许更多工具调用
        if (executedTools >= MAX_TOOL_CALLS) {
            return "本次工具调用已达到上限，"
                    + "无法继续执行新的操作。";
        }

        // 防止单轮工具调用突破总预算
        if (executedTools + toolCalls.size()
                > MAX_TOOL_CALLS) {

            return "本次请求的设备操作数量过多，"
                    + "一次最多允许 "
                    + MAX_TOOL_CALLS
                    + " 个操作，请拆分后再试。";
        }

        // 保存assistant消息和tool_calls
        Map<String, Object> assistantMessage =
                new LinkedHashMap<>();

        assistantMessage.put(
                "role",
                "assistant"
        );

        JsonNode contentNode =
                assistant.path("content");

        assistantMessage.put(
                "content",
                contentNode.isNull()
                || contentNode.isMissingNode()
                        ? null
                        : contentNode.asText()
        );

        assistantMessage.put(
                "tool_calls",
                toolCalls
        );

        messages.add(assistantMessage);

        // 情况二：逐个执行模型请求的工具
        for (int i = 0; i < toolCalls.size(); i++) {

            JsonNode call = toolCalls.path(i);

            String toolCallId =
                    call.path("id").asText();

            String toolName = call
                    .path("function")
                    .path("name")
                    .asText();

            String arguments = call
                    .path("function")
                    .path("arguments")
                    .asText();

            if (toolCallId.isBlank()
                    || toolName.isBlank()) {

                throw new IllegalStateException(
                        "工具调用缺少id或名称"
                );
            }

            long toolStartNs = System.nanoTime();
            String toolResult;

            // 不自动重试上一条结果不确定的设备操作
            boolean weatherNotReady =
                    weatherDependent
                    && (successfulWeatherRound < 0
                        || successfulWeatherRound >= round);

            if ("showExpression".equals(toolName)
                    && (blockFurtherExpressions
                        || weatherQueryFailed
                        || weatherNotReady)) {

                toolResult = errorResult(
                        weatherQueryFailed
                                ? "本次天气查询失败或数据无效，已禁止后续表情控制"
                                : weatherNotReady
                                        ? "天气相关设备控制必须先在上一轮成功查询天气，已阻止本次操作"
                                        : "上一条设备控制未确认成功，已阻止后续表情控制"
                );

            } else if ("showExpression".equals(toolName)
                    && weatherDependent
                    && !isAllowedWeatherAction(
                            successfulWeatherCondition,
                            arguments)) {

                // LESSON33_WEATHER_RULE_GUARD
                toolResult = errorResult(
                        "天气表情规则校验失败，"
                        + "目标表情与真实天气不符，"
                        + "或当前天气不支持自动切换，"
                        + "已阻止设备控制。"
                );

                log.warn("天气表情规则校验失败，已阻止设备控制");

            } else {

                toolResult = executeTool(
                        toolName,
                        arguments
                );

                if (toolResult == null) {
                    toolResult = errorResult(
                            "工具返回了空结果"
                    );
                }

                // 天气查询失败后，本次请求禁止后续表情控制
                if ("getCurrentWeather".equals(toolName)) {
                    try {
                        JsonNode weatherResult =
                                mapper.readTree(toolResult);

                        boolean validWeather =
                                weatherResult.path("success").asBoolean(false)
                                && weatherResult.path("condition").isTextual()
                                && weatherResult.path("weather_code").isNumber()
                                && weatherResult.path("time").isTextual();

                        if (validWeather) {
                            successfulWeatherRound = round;
                            successfulWeatherCondition =
                                    weatherResult.path("condition").asText();
                        }

                        if (!validWeather) {
                            weatherQueryFailed = true;
                            log.warn("天气查询失败或数据无效，已阻止后续表情控制");
                        }
                    } catch (Exception e) {
                        weatherQueryFailed = true;
                        log.warn("天气结果解析失败，已阻止后续表情控制");
                    }
                }

                // 若设备控制失败或结果不确定，
                // 阻止本次用户请求内再次控制表情。
                if ("showExpression".equals(toolName)) {

                    try {

                        JsonNode resultJson =

 /* 使用多轮 Tool Calling 循环处理模型请求。*/
        mapper.readTree(toolResult);

                        if (!resultJson.path("success")
                                .asBoolean(false)) {

                            blockFurtherExpressions = true;
                        }

                    } catch (Exception e) {

                        blockFurtherExpressions = true;
                    }
                }
            }

            executedTools++;

            AgentTraceRecorder.tool(
                    traceId,
                    round + 1,
                    toolCallId,
                    toolName,
                    arguments,
                    toolResult,
                    (System.nanoTime() - toolStartNs) / 1_000_000
            );

            log.info(
                    "工具执行完成：name={}, 已执行数量={}",
                    toolName,
                    executedTools
            );

            // 每个tool_call_id必须对应一条tool结果
            messages.add(Map.of(
                    "role", "tool",
                    "tool_call_id", toolCallId,
                    "content", toolResult
            ));
        }

        // 继续下一轮，让模型读取工具结果
    }

    throw new IllegalStateException(
            "Tool Calling循环超出允许轮次"
    );
}

// 判断当前请求是否依赖天气信息
private boolean isAllowedWeatherAction(
        String condition,
        String arguments) {

    String expected =
            WeatherExpressionPolicy.expectedExpression(condition);

    if (expected == null) {
        return false;
    }

    try {
        JsonNode args = mapper.readTree(arguments);

        return args.isObject()
                && args.size() == 1
                && args.path("action").isTextual()
                && expected.equals(args.path("action").asText());

    } catch (Exception e) {
        return false;
    }
}

private static boolean isWeatherDependentRequest(
        String userMessage) {

    if (userMessage == null) {
        return false;
    }

    String text = userMessage.toLowerCase(
            java.util.Locale.ROOT
    );

    return List.of(
            "天气", "气温", "下雨", "降雨", "降水",
            "晴天", "阴天", "多云",
            "weather", "forecast", "rainy",
            "sunny", "overcast", "temperature"
    ).stream().anyMatch(text::contains);
}

private String executeTool(
        String toolName,
        String arguments
) {

    if (!toolRegistry.contains(toolName)) {
        return errorResult(
                "不支持的工具：" + toolName
        );
    }

    return toolRegistry.execute(
            toolName,
            arguments
    );
}


    // 根据模型返回的信息执行 Java 工具

    /**
     * Lesson 29 / Stage 1: Send -> read back actual ESP32 state.
     *
     * This establishes that the state reported by the device matches
     * the requested expression. Without a command ID, it is NOT a
     * per-command completion acknowledgement or a hardware frame ack.
     * IMPORTANT: if the device was already in the requested state,
     * matching the state cannot prove that THIS command was applied.
     */

    // 将失败信息转换成工具结果 JSON
    private String errorResult(String message) {

        return mapper.writeValueAsString(Map.of(
                "success", false,
                "error", message
        ));
    }
}
