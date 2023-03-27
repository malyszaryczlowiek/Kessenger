FROM adoptopenjdk/openjdk11:jdk-11.0.11_9
# jdk-11.0.11_9 is currently latest
COPY target/scala-2.13/KafkaStreamsChatAnalyser-0.1.0.jar .
# COPY application.conf .
ENV KAFKA_STREAMS_ENV PROD
# RUN jar -uf KafkaStreamsChatAnalyser-0.1.0.jar application.conf
# EXPOSE 8080:8080
CMD ["java", "-jar", "KafkaStreamsChatAnalyser-0.1.0.jar"]
