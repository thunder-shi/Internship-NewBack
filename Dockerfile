# 构建阶段（需可拉取 maven:3.9-eclipse-temurin-25；若无该 tag，请改为与本机 JDK 一致的官方 Maven 镜像）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/newcms-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8111
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-jar", "/app/app.jar"]
