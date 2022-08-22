FROM bitnami/spark:3.3.0
COPY target/scala-2.12/SparkStreamingAnalyser-0.1.0.jar .
CMD ["./bin/spark-submit", "--master", "local", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--class", "com.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "100"]