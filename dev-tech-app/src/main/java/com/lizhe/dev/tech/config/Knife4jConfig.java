package com.lizhe.dev.tech.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j API文档配置
 * 
 * @author 李哲
 * @date 2025/6/17
 */
@Configuration
public class Knife4jConfig {

    /**
     * 配置API基本信息
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI知识库检索系统 API")
                        .version("1.0.0")
                        .description("基于 Ollama DeepSeek、OpenAI 大模型构建的增强型 RAG & MCP 智能知识库检索系统")
                        .contact(new Contact()
                                .name("李哲")
                                .email("2318691019@qq.com")
                                .url("https://github.com/lizhe-0423/ai-knowledge"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
//
//    /**
//     * 配置API分组 - Ollama相关接口
//     */
//    @Bean
//    public GroupedOpenApi ollamaApiGroup() {
//        return GroupedOpenApi.builder()
//                .group("Ollama AI")
//                .pathsToMatch("/api/v1/ollama/**")
//                .build();
//    }

//    /**
//     * 配置API分组 - OpenAI相关接口
//     */
//    @Bean
//    public GroupedOpenApi openAiApiGroup() {
//        return GroupedOpenApi.builder()
//                .group("OpenAI")
//                .pathsToMatch("/api/v1/openai/**")
//                .build();
//    }

    /**
     * 配置API分组 - 所有接口
     */
    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .pathsToMatch("/api/**")
                .build();
    }
}