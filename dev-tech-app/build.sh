# 普通镜像构建，随系统版本构建 amd/arm
docker build -t lvemiw9/ai-rag-knowledge-app:1.1 -f ./Dockerfile .

# 兼容 amd、arm 构建镜像
# docker buildx build --load --platform liunx/amd64,linux/arm64 -t lvemiw9/group-buy-market-app:1.2 -f ./Dockerfile . --push