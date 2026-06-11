FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app

ENV TZ=Asia/Shanghai

COPY target/blade-backend-*.jar /app/blade-backend.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/blade-backend.jar"]
