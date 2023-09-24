FROM bitnami/spark:3.3.0
COPY ./spark-graphx-analyser-0.1.0.jar .
COPY ./postgresql-42.3.3.jar .
COPY ./log4j2.xml $SPARK_HOME/conf
CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0,org.slf4j:slf4j-nop:2.0.5,org.postgresql:postgresql:42.3.3", "--driver-class-path", "postgresql-42.3.3.jar", "--class", "io.github.malyszaryczlowiek.SparkGraphX", "/opt/bitnami/spark/spark-graphx-analyser-0.1.0.jar", "1000"]
