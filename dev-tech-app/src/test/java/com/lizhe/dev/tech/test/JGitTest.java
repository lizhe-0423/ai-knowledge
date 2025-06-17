package com.lizhe.dev.tech.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class JGitTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void test() throws Exception {
        // todo 替换账密
        String repoURL = "";
        String username = "";
        String password = "";

        String localPath = "./cloned-repo";
        log.info("克隆路径：" + new File(localPath).getAbsolutePath());

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoURL)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .call();

        git.close();
    }

    @Test
    public void test_file() throws IOException {
        Files.walkFileTree(Paths.get("./cloned-repo"), new SimpleFileVisitor<>() {
            @NotNull
            @Override
            public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                // 跳过.git目录下的所有文件
                if (file.toString().contains(".git")) {
                    return FileVisitResult.CONTINUE;
                }

                // 只处理常见的文档文件类型
                String fileName = file.getFileName().toString().toLowerCase();
                if (!isDocumentFile(fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                // 检查文件大小，跳过空文件
                if (attrs.size() == 0) {
                    return FileVisitResult.CONTINUE;
                }

                log.info("文件路径:{}", file);

                try {
                    PathResource resource = new PathResource(file);
                    TikaDocumentReader reader = new TikaDocumentReader(resource);

                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    documents.forEach(doc -> doc.getMetadata().put("knowledge", "ai-knowledge"));
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "ai-knowledge"));

                    pgVectorStore.accept(documentSplitterList);
                } catch (Exception e) {
                    log.error("处理文件失败: {} - {}", file, e.getMessage());
                }

                return FileVisitResult.CONTINUE;
            }

            @NotNull
            @Override
            public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                // 跳过.git目录
                if (dir.getFileName().toString().equals(".git")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

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
