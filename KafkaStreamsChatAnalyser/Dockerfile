FROM adoptopenjdk/openjdk11:jdk-11.0.11_9
# jdk-11.0.11_9 is currently latest
COPY target/scala-3.1.1/KafkaStreamsChatAnalyser-0.1.0.jar .


# COPY toDelete/back-end-1.0-SNAPSHOT ./opt/back-end
# COPY toDelete/generatedKey ./opt/back-end
#
# CMD ["export", "PLAY_SECRET_KEY=$(cat generatedKey)"]
# CMD ["echo", "$PLAY_SECRET_KEY"]



# EXPOSE 8080:8080
CMD ["java", "-jar", "KafkaStreamsChatAnalyser-0.1.0.jar"]
