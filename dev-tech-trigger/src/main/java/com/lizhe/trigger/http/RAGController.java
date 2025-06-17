package com.lizhe.trigger.http;

import com.lizhe.dev.tech.api.IRAGService;
import com.lizhe.dev.tech.api.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAGController
 * {@code @description} RAG知识库管理控制类
 *
 * @author 李哲
 * {@code @date} 2025/6/16 14:03
 * @version 1.0
 */
@Tag(name = "RAG知识库接口", description = "基于RAG的知识库管理接口")
@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;


    /**
     * 查询知识库标签列表接口
     * <a href="http://localhost:8090/api/v1/rag/query_rag_tag_list">测试链接</a>
     */
    @Operation(summary = "查询知识库标签列表", description = "获取所有已创建的知识库标签列表")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "成功获取标签列表"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @Override
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder().code("200").info("调用成功").data(elements).build();
    }

    /**
     * 文件上传到知识库接口
     * <a href="http://localhost:8090/api/v1/rag/file/upload">测试链接</a>
     */
    @Operation(summary = "上传文件到知识库", description = "将文档文件上传并存储到指定的知识库标签中，支持多文件上传")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "文件上传成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误或文件格式不支持"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(
            @Parameter(description = "知识库标签名称", example = "技术文档", required = true)
            @RequestParam() String ragTag,
            @Parameter(description = "要上传的文件列表，支持PDF、Word、TXT等格式", required = true)
            @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);
        for (MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);

            RList<String> elements = redissonClient.getList("ragTag");
            if (!elements.contains(ragTag)) {
                elements.add(ragTag);
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("200").info("调用成功").build();
    }
}
