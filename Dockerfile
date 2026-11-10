# Image hddt-data-api-service (Java 8, non-root — tương thích SCC restricted của OpenShift).
# Java 8: cần JRE >= 8u191 cho -XX:MaxRAMPercentage (nhận giới hạn bộ nhớ container).
# Build: mvn -B package -DskipTests && docker build -t <registry>/hddt-data-api-service:<tag> .
FROM eclipse-temurin:8-jre
WORKDIR /app
COPY target/hddt-data-api-service.jar /app/app.jar
USER 1001
EXPOSE 8080
# Mã tự dùng Asia/Ho_Chi_Minh khi định dạng ngày; JVM để UTC để phát hiện phụ thuộc múi giờ ngầm.
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
