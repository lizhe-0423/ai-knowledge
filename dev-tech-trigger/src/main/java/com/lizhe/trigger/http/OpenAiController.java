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
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAiController
 * {@code @description} OpenAI控制类
 *
 * @author 李哲
 * {@code @date} 2025/6/16 14:03
 * @version 1.0
 */
@Tag(name = "OpenAI接口", description = "基于OpenAI的AI对话接口")
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatClient chatClient;
    @Resource
    private PgVectorStore pgVectorStore;

    /**
     * OpenAI对话生成接口
     * <p>
     * 使用OpenAI的指定模型生成AI回复，支持GPT-3.5、GPT-4等模型。
     * 该接口为同步调用，会等待完整响应后返回结果。
     * </p>
     * 
     * <a href="http://localhost:8090/api/v1/openai/generate?model=gpt-3.5-turbo&message=你好">测试链接</a>
     * 
     * @param model OpenAI模型名称，如"gpt-3.5-turbo"、"gpt-4"等
     * @param message 用户输入的消息内容
     * @return ChatResponse 包含AI生成回复的响应对象
     * 
     * @since 1.0
     * @author 李哲
     */
    @Operation(summary = "OpenAI对话生成", description = "使用指定的OpenAI模型生成AI回复")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功生成回复"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "OpenAI API密钥无效"),
            @ApiResponse(responseCode = "429", description = "API调用频率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    @Override
    public ChatResponse generate(
            @Parameter(description = "OpenAI模型名称", example = "gpt-3.5-turbo", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "用户消息内容", example = "你好，请介绍一下自己", required = true)
            @RequestParam(name = "message") String message) {
        return chatClient.call(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }


    /**
     * OpenAI对话流式生成接口
     * <p>
     * 使用OpenAI的指定模型进行流式生成，支持实时返回生成内容。
     * 适用于需要实时显示生成过程的场景，提供更好的用户体验。
     * </p>
     * 
     * <a href="http://localhost:8090/api/v1/openai/generate_stream?model=gpt-3.5-turbo&message=请写一首关于春天的诗">测试链接</a>
     * 
     * @param model OpenAI模型名称，如"gpt-3.5-turbo"、"gpt-4"等
     * @param message 用户输入的消息内容
     * @return Flux&lt;ChatResponse&gt; 流式响应对象，包含实时生成的内容
     * 
     * @apiNote
     * <ul>
     *   <li>响应格式：Server-Sent Events (SSE) 流</li>
     *   <li>支持实时显示生成过程</li>
     *   <li>适合长文本生成场景</li>
     * </ul>
     * 
     * @since 1.0
     * @author 李哲
     */
    @Operation(summary = "OpenAI对话流式生成", 
               description = "使用指定的OpenAI模型流式生成AI回复，支持实时返回")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始流式生成"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "OpenAI API密钥无效"),
            @ApiResponse(responseCode = "429", description = "API调用频率限制"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStream(
            @Parameter(description = "OpenAI模型名称", example = "gpt-3.5-turbo", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "用户消息内容", example = "请写一首关于春天的诗", required = true)
            @RequestParam(name = "message") String message) {
        return chatClient.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    /**
     * 基于RAG的OpenAI对话流式生成接口
     * <p>
     * 该接口结合检索增强生成(RAG)技术，从向量数据库中检索相关文档，
     * 并基于检索到的文档内容使用OpenAI模型生成更准确、更有针对性的AI回复。
     * 支持流式响应，可实时返回生成的内容。
     * </p>
     * 
     * <p>处理流程：</p>
     * <ol>
     *   <li>根据用户消息和ragTag从PostgreSQL向量数据库中检索相关文档</li>
     *   <li>将检索到的文档内容作为上下文信息构建系统提示词</li>
     *   <li>组装用户消息和系统消息</li>
     *   <li>调用OpenAI模型进行流式生成</li>
     * </ol>
     * 
     * <a href="http://localhost:8090/api/v1/openai/generate_stream_rag?model=gpt-3.5-turbo&ragTag=spring-ai&message=什么是RAG">测试链接</a>
     * 
     * @param model 指定的OpenAI模型名称，如"gpt-3.5-turbo"、"gpt-4"等
     * @param ragTag 知识库标签，用于过滤检索范围，对应向量数据库中的knowledge字段
     * @param message 用户输入的问题或消息内容
     * @return Flux&lt;ChatResponse&gt; 流式响应对象，包含AI生成的回复内容
     * 
     * @apiNote
     * <ul>
     *   <li>响应格式：Server-Sent Events (SSE) 流</li>
     *   <li>检索文档数量：最多5个相关文档</li>
     *   <li>回复语言：强制中文回复</li>
     *   <li>向量搜索：基于语义相似度检索</li>
     *   <li>模型提供商：OpenAI</li>
     * </ul>
     * 
     * @since 1.0
     * @author 李哲
     */
    @Operation(summary = "基于RAG的OpenAI对话流式生成", 
               description = "结合检索增强生成技术，从知识库中检索相关文档并使用OpenAI模型生成回复，支持流式响应")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功开始RAG流式生成"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或ragTag不存在"),
            @ApiResponse(responseCode = "401", description = "OpenAI API密钥无效"),
            @ApiResponse(responseCode = "429", description = "OpenAI API调用频率限制"),
            @ApiResponse(responseCode = "500", description = "向量检索或模型调用失败")
    })
    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    @Override
    public Flux<ChatResponse> generateStreamRag(
            @Parameter(description = "OpenAI模型名称", example = "gpt-3.5-turbo", required = true)
            @RequestParam(name = "model") String model,
            @Parameter(description = "知识库标签，用于指定检索范围", example = "spring-ai", required = true)
            @RequestParam(name = "ragTag") String ragTag,
            @Parameter(description = "用户问题或消息内容", example = "什么是RAG技术？", required = true)
            @RequestParam(name = "message") String message) {

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
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

}