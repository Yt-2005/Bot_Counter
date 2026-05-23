FROM eclipse-temurin:17-jre
COPY target/TelegramBot-1.0.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]cd C:\Code\TelegramBot
mvn clean package