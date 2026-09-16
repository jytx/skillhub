# Build the architecture-neutral JAR on the Buildx host. This avoids emulating
# the complete Maven build when the target image is linux/riscv64.
# 基础镜像使用 DaoCloud 国内加速源全限定名，任意国内环境可直接 docker build
FROM --platform=$BUILDPLATFORM docker.m.daocloud.io/library/eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
COPY skillhub-domain/pom.xml skillhub-domain/
COPY skillhub-auth/pom.xml skillhub-auth/
COPY skillhub-search/pom.xml skillhub-search/
COPY skillhub-infra/pom.xml skillhub-infra/
COPY skillhub-storage/pom.xml skillhub-storage/
COPY skillhub-notification/pom.xml skillhub-notification/
COPY skillhub-app/pom.xml skillhub-app/
COPY mvnw .
COPY .mvn .mvn
# Maven 本地仓库走 BuildKit 缓存挂载：依赖跨构建持久复用，不进镜像层
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 ./mvnw dependency:go-offline -B

COPY . .
RUN --mount=type=cache,id=maven-repo,target=/root/.m2 ./mvnw package -DskipTests -B

# ---- Runtime Stage ----
# The Noble variant publishes a linux/riscv64 image; the Alpine JRE currently
# used by this project is limited to amd64 and arm64.
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-noble
# Keep the Alpine image's numeric IDs so existing storage volumes remain writable.
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 101 app && \
    useradd --system --uid 100 --gid app --create-home app
WORKDIR /app

COPY --from=build /app/skillhub-app/target/*.jar app.jar

# Create storage directory and set permissions
RUN mkdir -p /var/lib/skillhub/storage && \
    chown -R app:app /app /var/lib/skillhub/storage

USER app

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
