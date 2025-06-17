package com.lizhe.dev.tech.test;

import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        // 使用Tika文档读取器读取指定路径的文本文件
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.text");

        // 获取原始文档列表
        List<Document> documents = reader.get();

        // 使用分词器将长文档分割成较小的文档块，便于向量化和检索
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 为原始文档添加知识库标识元数据，用于后续过滤和分类
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 为分割后的文档块也添加相同的知识库标识元数据
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "知识库名称"));

        // 将分割后的文档块存储到PgVector向量数据库中，自动生成向量嵌入
        pgVectorStore.accept(documentSplitterList);

        log.info("上传完成");
    }

    @Test
    public void chat() {

        String message = "王大瓜，哪年出生";

        // 定义系统提示词模板，用于指导AI如何使用检索到的文档信息回答问题
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 创建向量搜索请求，设置查询内容、返回前5个最相似的结果，并过滤特定知识库
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '知识库名称'");

        // 在PgVector向量数据库中执行相似性搜索，获取相关文档
        List<Document> documents = pgVectorStore.similaritySearch(request);

        // 将检索到的文档内容合并成一个字符串
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // 使用系统提示词模板创建系统消息，将检索到的文档内容填入模板
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        // 创建消息列表，包含用户问题和系统提示
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));  // 添加用户消息
        messages.add(ragMessage);                // 添加包含检索文档的系统消息

        // 调用Ollama聊天客户端，使用deepseek-r1:1.5b模型生成回答
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));


        log.info("测试结果:{}", JSON.toJSONString(chatResponse));

    }

}
