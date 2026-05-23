FROM eclipse-temurin:17-jre 
COPY target/TelegramBot-1.0.jar app.jar 
CMD ["java", "-jar", "app.jar"] 
