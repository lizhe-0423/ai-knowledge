# 🚀 AI Knowledge Base Retrieval System

[中文版](README_CN.md) | **English**

> An enhanced RAG (Retrieval-Augmented Generation) intelligent knowledge base system built on Spring AI framework with Ollama and OpenAI integration

## 📖 Project Overview

This project is an intelligent knowledge base system that integrates Retrieval-Augmented Generation (RAG) technology, designed to provide comprehensive AI-assisted solutions for enterprises. By combining the capabilities of multiple large language models, it achieves end-to-end intelligent processing from document parsing to intelligent Q&A.

## ✨ Core Features

### 🔍 RAG (Retrieval-Augmented Generation)

**Key Capabilities:**
- 📄 **Multi-format Document Processing**: Support for PDF, Word, Markdown, and other document formats via Apache Tika
- 🔗 **Git Repository Integration**: Automatic repository cloning and code analysis using JGit
- 🧠 **Dual Embedding Models**: 
  - Local `nomic-embed-text` model via Ollama for privacy and cost control
  - OpenAI `text-embedding-ada-002` for high-quality embeddings
- 🗄️ **Vector Storage**: PostgreSQL with pgvector extension for persistent vector storage
- 🔄 **Flexible Model Switching**: Configuration-based switching between local and cloud models

**Technical Benefits:**
- Enhanced search accuracy through semantic understanding
- Cost-effective hybrid model approach
- Scalable vector storage solution
- Privacy-preserving local processing option

### 🤖 AI-Powered Q&A System

**Core Workflow:**
1. **Document Ingestion**: Parse and chunk documents using Spring AI Tika integration
2. **Vector Embedding**: Convert text to vectors using selected embedding model
3. **Semantic Search**: Retrieve relevant documents from vector database
4. **Answer Generation**: Generate contextual responses using OpenAI GPT models

**Application Scenarios:**
- Enterprise knowledge management
- Technical documentation Q&A
- Code repository analysis and search
- Intelligent customer support

## 🏗️ Technical Architecture

### Supported AI Models
- **Ollama Models**: Local deployment with `nomic-embed-text` for embedding
- **OpenAI GPT Series**: Cloud-based models for text generation and embedding
- **Extensible Framework**: Easy integration of additional model providers

### Core Technology Stack
- **Backend Framework**: Spring Boot 3.2.3 with Spring AI
- **Vector Database**: PostgreSQL with pgvector extension
- **Caching**: Redis for performance optimization
- **Document Processing**: Apache Tika for multi-format support
- **API Documentation**: Swagger UI with Knife4j enhancements
- **Containerization**: Docker support for easy deployment

### Key Dependencies
- Spring AI BOM for AI model integration
- Redisson for Redis operations
- JGit for Git repository handling
- FastJSON for JSON processing
- HikariCP for database connection pooling

## 🚀 Quick Start

### Prerequisites
- Java 17+
- PostgreSQL with pgvector extension
- Redis server
- Ollama (for local models)
- OpenAI API key (for cloud models)

### Configuration

1. **Database Setup**: Configure PostgreSQL connection in `application-dev.yml`
2. **AI Models**: Set up Ollama locally or configure OpenAI API credentials
3. **Vector Storage**: Choose between SimpleVectorStore (memory) or PgVectorStore (persistent)
4. **Embedding Model**: Configure `spring.ai.rag.embed` to select embedding model

### Running the Application

```bash
# Clone the repository
git clone <repository-url>

# Navigate to the project directory
cd ai-knowledge

# Run with Maven
mvn spring-boot:run -pl dev-tech-app
```

The application will start on port 8090 with Swagger UI available at `/swagger-ui.html`.

## 📊 System Architecture

![System Architecture](docs/images/系统架构.png)

## 📊 RAG Workflow

![RAG Workflow](docs/images/RAG流程.png)

## 🔧 Configuration Options

### Embedding Model Selection
- **Local Model**: Set `spring.ai.rag.embed=nomic-embed-text` for privacy and cost savings
- **Cloud Model**: Set `spring.ai.rag.embed=text-embedding-ada-002` for higher quality

### Vector Storage Options
- **Memory Storage**: `SimpleVectorStore` for development and testing
- **Persistent Storage**: `PgVectorStore` for production environments

## 🤝 Contributing

We welcome contributions! Please feel free to submit issues and pull requests.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

