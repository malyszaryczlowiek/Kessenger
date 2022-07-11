FROM adoptopenjdk/openjdk11:jdk-11.0.11_9
# jdk-11.0.11_9 is currently latest
COPY target/scala-3.1.1/KafkaStreamsChatAnalyser-0.1.0.jar .
# EXPOSE 8080:8080
CMD ["java", "-jar", "KafkaStreamsChatAnalyser-0.1.0.jar"]
