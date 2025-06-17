package com.lizhe.trigger.http;

import com.lizhe.dev.tech.api.IAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * OllamaController
 * {@code @description} Ollama控制类
 *
 * @author 李哲
 * {@code @date} 2025/6/16 14:03
 * @version 1.0
 */
@Tag(name = "Ollama AI接口", description = "基于Ollama的AI对话接口")
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient chatClient;

    /**
     * AI对话生成接口
     * <a href="http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=1+1">测试链接</a>
     */
    @Operation(summary = "AI对话生成", description = "使用指定模型生成AI回复")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功生成回复"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(
            @Parameter(description = "AI模型名称", example = "deepseek-r1:1.5b", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "用户消息内容", example = "你好，请介绍一下自己", required = true)
            @RequestParam(name = "message") String message) {
        return chatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * AI对话流式生成接口
     * <a href="http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi">测试链接</a>
     */
    @Operation(summary = "AI对话流式生成", description = "使用指定模型流式生成AI回复，支持实时返回")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始流式生成"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStream(
            @Parameter(description = "AI模型名称", example = "deepseek-r1:1.5b", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "用户消息内容", example = "请写一首关于春天的诗", required = true)
            @RequestParam(name = "message") String message) {
        return chatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    @Override
    public Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message) {
        return null;
    }
}
