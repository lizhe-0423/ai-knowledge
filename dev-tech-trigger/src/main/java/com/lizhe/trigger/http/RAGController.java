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
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
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
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
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

        // 添加重试机制，处理网络连接重置问题
        Git git = null;
        int maxRetries = 3;
        int retryCount = 0;
        boolean success = false;
        Exception lastException = null;

        while (!success && retryCount < maxRetries) {
            try {
                // 使用JGit克隆远程Git仓库到本地，设置超时时间
                log.info("开始克隆仓库（尝试 {} / {}）: {}", retryCount + 1, maxRetries, repoUrl);
                git = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(new File(localPath))
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
                        .setTimeout(300) // 设置超时时间为300秒
                        .call();
                success = true;
                log.info("仓库克隆成功: {}", repoUrl);
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                log.warn("仓库克隆失败（尝试 {} / {}）: {} - {}", retryCount, maxRetries, repoUrl, e.getMessage());
                if (retryCount < maxRetries) {
                    try {
                        // 等待一段时间后重试
                        int waitTime = 2000 * retryCount; // 递增等待时间
                        log.info("等待 {} 毫秒后重试...", waitTime);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("克隆操作被中断", ie);
                    }
                }
            }
        }

        // 如果所有重试都失败，抛出最后捕获的异常
        if (!success) {
            throw new RuntimeException("在 " + maxRetries + " 次尝试后仍无法克隆仓库: " + repoUrl, lastException);
        }

        // 使用Files.walkFileTree遍历克隆的仓库目录树，处理每个文件
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            @NotNull
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                // 跳过.git目录下的所有文件
                if (file.toString().contains(".git")) {
                    return FileVisitResult.CONTINUE;
                }

                // 只处理常见的文档文件类型
                String fileName = file.getFileName().toString().toLowerCase();
                if (!isDocumentFile(fileName)) {
                    log.info("跳过非文档文件: {}", fileName);
                    return FileVisitResult.CONTINUE;
                }

                // 检查文件大小，跳过空文件
                if (attrs.size() == 0) {
                    log.info("跳过空文件: {}", fileName);
                    return FileVisitResult.CONTINUE;
                }

                log.info("{} 遍历解析路径，上传知识库:{}", repoProjectName, file.getFileName());
                try {
                    // 使用TikaDocumentReader读取文件内容，支持多种文件格式
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    processDocuments(reader, repoProjectName);
                } catch (Exception e) {
                    // 记录文件处理失败的错误，但继续处理其他文件
                    log.error("遍历解析路径，上传知识库失败:{} - {}", file.getFileName(), e.getMessage());
                }

                // 继续遍历下一个文件
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NotNull
            public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) {
                // 记录无法访问的文件，但继续遍历
                log.info("Failed to access file: {} - {}", file, exc.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            @NotNull
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                // 跳过.git目录
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
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

    /**
     * 判断文件是否为支持的文档类型
     *
     * @param fileName 文件名
     * @return 如果是支持的文档类型返回true，否则返回false
     */
    private boolean isDocumentFile(String fileName) {
        return fileName.endsWith(".txt") ||
                fileName.endsWith(".md") ||
                fileName.endsWith(".pdf") ||
                fileName.endsWith(".doc") ||
                fileName.endsWith(".docx") ||
                fileName.endsWith(".java") ||
                fileName.endsWith(".py") ||
                fileName.endsWith(".js") ||
                fileName.endsWith(".html") ||
                fileName.endsWith(".xml") ||
                fileName.endsWith(".json");
    }
}
