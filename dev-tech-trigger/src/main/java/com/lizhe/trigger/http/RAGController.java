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
import org.jetbrains.annotations.NotNull;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
    private TokenTextSplitter tokenTextSplitter;
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
            @RequestParam("ragTag") String ragTag,
            @Parameter(description = "要上传的文件列表，支持PDF、Word、TXT等格式", required = true)
            @RequestParam("file") List<MultipartFile> files) {
        log.info("上传知识库开始 {}", ragTag);
        for (MultipartFile file : files) {
            // 使用Tika文档读取器解析上传的文件
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            processDocuments(documentReader, ragTag);
        }

        // 将标签添加到Redis中的知识库标签列表
        addRagTagToRedis(ragTag);

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("200").info("调用成功").build();
    }

    /**
     * 分析Git仓库并导入知识库
     * <a href="http://localhost:8090/api/v1/rag/analyze_git_repository">测试链接</a>
     */
    @Operation(summary = "分析Git仓库", description = "克隆指定的Git仓库，解析其中的文件并导入到RAG知识库")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Git仓库分析成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "500", description = "服务器内部错误")
    })
    @Override
    public Response<String> analyzeGitRepository(
            @Parameter(description = "Git仓库URL地址", example = "https://github.com/user/repo.git", required = true)
            @RequestParam("repoUrl") String repoUrl,
            @Parameter(description = "Git用户名（用于认证）", example = "username", required = true)
            @RequestParam("userName") String userName,
            @Parameter(description = "Git访问令牌（用于认证）", example = "ghp_xxxxxxxxxxxx", required = true)
            @RequestParam("token") String token) throws Exception {

        // 定义本地克隆路径
        String localPath = "./git-cloned-repo";
        // 从仓库URL中提取项目名称，用作知识库标签
        String repoProjectName = extractProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        // 删除已存在的本地目录，确保干净的克隆环境
        FileUtils.deleteDirectory(new File(localPath));

        // 使用JGit克隆远程Git仓库到本地
        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                .call();

        // 使用Files.walkFileTree遍历克隆的仓库目录树，处理每个文件
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            @NotNull
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());
                try {
                    // 使用TikaDocumentReader读取文件内容，支持多种文件格式
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    processDocuments(reader, repoProjectName);
                } catch (Exception e) {
                    // 记录文件处理失败的错误，但继续处理其他文件
                    log.error("遍历解析路径，上传知识库失败:{}", file.getFileName());
                }

                // 继续遍历下一个文件
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NotNull
            public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) throws IOException {
                // 记录无法访问的文件，但继续遍历
                log.info("Failed to access file: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });

        // 清理本地克隆的临时目录
        FileUtils.deleteDirectory(new File(localPath));

        // 将项目名称添加到Redis中的知识库标签列表
        addRagTagToRedis(repoProjectName);

        // 关闭Git资源
        git.close();

        log.info("遍历解析路径，上传完成:{}", repoUrl);

        return Response.<String>builder().code("200").info("调用成功").build();
    }


    /**
     * 处理文档：读取、分割、添加元数据并存储到向量数据库
     *
     * @param documentReader 文档读取器
     * @param ragTag         知识库标签
     */
    private void processDocuments(TikaDocumentReader documentReader, String ragTag) {
        // 读取文档内容，将文件转换为Document对象列表
        List<Document> documents = documentReader.get();

        // 使用Token文本分割器将长文档切分成较小的文档片段，便于向量化和检索
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // 为原始文档添加知识库标签元数据，用于后续的文档分类和检索
        documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

        // 为分割后的文档片段也添加相同的知识库标签元数据
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

        // 将分割后的文档片段存储到PostgreSQL向量数据库中，生成向量嵌入用于相似性搜索
        pgVectorStore.accept(documentSplitterList);
    }

    /**
     * 将知识库标签添加到Redis列表中（如果不存在）
     *
     * @param ragTag 知识库标签
     */
    private void addRagTagToRedis(String ragTag) {
        RList<String> elements = redissonClient.getList("ragTag");
        if (!elements.contains(ragTag)) {
            elements.add(ragTag);
        }
    }

    private String extractProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
