package com.lizhe.trigger.http;

import com.lizhe.dev.tech.api.IAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Resource(name = "customOllamaChatClient")
    private OllamaChatClient chatClient;

    @Resource
    private PgVectorStore pgVectorStore;

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

    /**
     * 基于RAG的AI对话流式生成接口
     * <p>
     * 该接口结合检索增强生成(RAG)技术，从向量数据库中检索相关文档，
     * 并基于检索到的文档内容生成更准确、更有针对性的AI回复。
     * 支持流式响应，可实时返回生成的内容。
     * </p>
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据用户消息和ragTag从PostgresSQL向量数据库中检索相关文档</li>
     *   <li>将检索到的文档内容作为上下文信息构建系统提示词</li>
     *   <li>组装用户消息和系统消息</li>
     *   <li>调用Ollama模型进行流式生成</li>
     * </ol>
     *
     * <a href="http://localhost:8090/api/v1/ollama/generate_stream_rag?model=deepseek-r1:1.5b&ragTag=spring-ai&message=什么是RAG">测试链接</a>
     *
     * @param model   指定的AI模型名称，如"deepseek-r1:1.5b"、"llama2"等
     * @param ragTag  知识库标签，用于过滤检索范围，对应向量数据库中的knowledge字段
     * @param message 用户输入的问题或消息内容
     * @return Flux&lt;ChatResponse&gt; 流式响应对象，包含AI生成的回复内容
     * @apiNote <ul>
     * <li>响应格式：Server-Sent Events (SSE) 流</li>
     * <li>检索文档数量：最多5个相关文档</li>
     * <li>回复语言：强制中文回复</li>
     * <li>向量搜索：基于语义相似度检索</li>
     * </ul>
     * @author 李哲
     * @since 1.0
     */
    @Operation(summary = "基于RAG的AI对话流式生成",
            description = "结合检索增强生成技术，从知识库中检索相关文档并生成AI回复，支持流式响应")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始RAG流式生成"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或ragTag不存在"),
            @ApiResponse(responseCode = "500", description = "向量检索或模型调用失败")
    })
    @Override
    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStreamRag(
            @Parameter(description = "AI模型名称", example = "deepseek-r1:1.5b", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "知识库标签，用于指定检索范围", example = "spring-ai", required = true)
            @RequestParam(name = "ragTag") String ragTag,
            @Parameter(description = "用户问题或消息内容", example = "什么是RAG技术？", required = true)
            @RequestParam(name = "message") String message) {
        // 提示词
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 指定文档搜索
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        List<Document> documents = pgVectorStore.similaritySearch(request);
        String documentCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentCollectors));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        return chatClient.stream(new Prompt(
                messages,
                OllamaOptions.create()
                        .withModel(model)
        ));
    }
}
