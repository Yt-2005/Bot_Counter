FROM openjdk:17
COPY target/TelegramBot-1.0.jar app.jar
CMD ["java", "-jar", "app.jar"]