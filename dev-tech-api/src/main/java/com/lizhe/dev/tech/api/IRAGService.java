package com.lizhe.dev.tech.api;


import com.lizhe.dev.tech.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG（检索增强生成）服务接口
 * <p>
 * 提供基于RAG技术的知识库管理和检索功能，包括：
 * <ul>
 *     <li>知识库标签管理</li>
 *     <li>文档上传和解析</li>
 *     <li>Git仓库分析</li>
 * </ul>
 * </p>
 *
 * @author lizhe
 * @since 1.0.0
 */
public interface IRAGService {

    /**
     * 查询RAG知识库标签列表
     * <p>
     * 获取系统中已存在的所有知识库标签，用于分类管理不同类型的知识文档
     * </p>
     *
     * @return 包含标签列表的响应对象
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传文件到RAG知识库
     * <p>
     * 将文件上传到指定标签的知识库中，系统会自动解析文档内容并进行向量化存储
     * </p>
     *
     * @param ragTag 知识库标签，用于分类存储文档
     * @param files  待上传的文件列表，支持多种文档格式
     * @return 上传结果响应对象
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

    /**
     * 分析Git仓库
     * <p>
     * 分析指定的Git仓库，提取代码和文档信息并存储到知识库中
     * </p>
     *
     * @param repoUrl  Git仓库URL地址
     * @param userName Git用户名（如果需要认证）
     * @param token    Git访问令牌（如果需要认证）
     * @return 分析结果响应对象
     * @throws Exception 当仓库访问失败或分析过程中出现错误时抛出异常
     */
    Response<String> analyzeGitRepository(String repoUrl, String userName, String token) throws Exception;

}
