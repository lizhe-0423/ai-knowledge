package com.lizhe.dev.tech.api;


import org.springframework.ai.chat.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * AI服务接口
 * <p>提供AI模型的对话生成功能，支持同步和异步流式响应</p>
 *
 * @author 李哲
 * @version 1.0
 * @since 2025/6/16
 */
public interface IAiService {

    /**
     * 生成AI响应（同步方式）
     *
     * @param model   使用的AI模型名称（如：gpt-3.5-turbo, gpt-4等）
     * @param message 用户输入的消息内容
     * @return ChatResponse AI生成的响应结果
     * @throws IllegalArgumentException 当model或message为空时抛出
     * @throws RuntimeException         当AI服务调用失败时抛出
     */
    ChatResponse generate(String model, String message);

    /**
     * 生成AI响应（流式异步方式）
     * <p>适用于需要实时显示AI生成过程的场景，如聊天界面</p>
     *
     * @param model   使用的AI模型名称
     * @param message 用户输入的消息内容
     * @return Flux<ChatResponse> 响应流，可以逐步接收AI生成的内容
     * @throws IllegalArgumentException 当model或message为空时抛出
     */
    Flux<ChatResponse> generateStream(String model, String message);
}
