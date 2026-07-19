FROM eclipse-temurin:17-jdk-slim
WORKDIR /app
# Maven打包产物
COPY target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar","--server.port=${PORT}"]