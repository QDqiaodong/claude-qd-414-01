ARG DOCKER_REGISTRY=docker.io/library/
FROM ${DOCKER_REGISTRY}maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml settings.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -s settings.xml dependency:go-offline -q
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -s settings.xml package -DskipTests -q

ARG DOCKER_REGISTRY=docker.io/library/
FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre
WORKDIR /app
ENV LANG=zh_CN.UTF-8
ENV LC_ALL=zh_CN.UTF-8
ENV TZ=Asia/Shanghai
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
COPY --from=build /app/target/*.jar app.jar
ARG BACKEND_INTERNAL_PORT=8080
EXPOSE ${BACKEND_INTERNAL_PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
