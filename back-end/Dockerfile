FROM adoptopenjdk/openjdk11:jdk-11.0.11_9
COPY target/universal/back-end-1.0-SNAPSHOT /opt/back-end
COPY generatedKey /opt/back-end
RUN mkdir /var/logs
ENV KESSENGER_LOGBACK_ENV PROD
CMD ["echo", "$KESSENGER_LOGBACK_ENV"]
CMD ["/opt/back-end/bin/back-end", "-Dplay.http.secret.key=$(cut /opt/back-end/generatedKey)", "-Dconfig.resource=prod.conf"]






# CMD ["apt-get", "update"]
# CMD ["apt-get", "install", "unzip"]
# CMD ["export", "PLAY_SECRET_KEY=$(cat generatedKey)"]
# CMD ["echo", "$PLAY_SECRET_KEY"]
# CMD ["rm", "generatedKey"]
# CMD ["./back-end-1.0-SNAPSHOT/bin/back-end", "-Dplay.http.secret.key=$(cut /opt/back-end/generatedKey)"]



# CMD ["unzip", "back-end-1.0-SNAPSHOT.zip"]
# EXPOSE 8080:8080
#