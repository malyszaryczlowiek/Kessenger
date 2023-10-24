FROM bitnami/spark:3.3.0
COPY ./spark-streaming-analyser-0.1.0.jar .
COPY ./log4j2.xml $SPARK_HOME/conf
COPY ./postgresql-42.3.3.jar .
CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0,org.slf4j:slf4j-nop:2.0.5,org.postgresql:postgresql:42.3.3", "--driver-class-path", "postgresql-42.3.3.jar", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/spark-streaming-analyser-0.1.0.jar", "1000"]



# ENV SPARK_ENV PROD # unused



# without postgres
# CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0,org.slf4j:slf4j-nop:2.0.5", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]
# with postgres
