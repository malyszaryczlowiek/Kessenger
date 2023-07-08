FROM bitnami/spark:3.3.0
COPY target/scala-2.12/SparkStreamingAnalyser-0.1.0.jar .
COPY conf/dev/log4j2.xml $SPARK_HOME/conf
# RUN mkdir /opt/work-dir
# COPY log/application.log /opt/work-dir/application.log
CMD ["./bin/spark-submit", "--master", "local[2]", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0,org.slf4j:slf4j-nop:2.0.5", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]


# org.slf4j:slf4j-nop:2.0.5

# CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]
# CMD ["./bin/spark-submit", "--deploy-mode", "cluster", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]
# CMD ["./bin/spark-submit", "--deploy-mode", "cluster", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--conf", "spark.standalone.submit.waitAppCompletion=true", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]
# CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--conf", "spark.standalone.submit.waitAppCompletion=true", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "1000"]

# spark.standalone.submit.waitAppCompletion

# added slaves but this not helped
# RUN $SPARK_HOME/sbin/start-slaves.sh spark://spark-master:7077

# changed --master parameter
# CMD ["./bin/spark-submit", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "100"]

# --deploy-mode cluster added
# CMD ["./bin/spark-submit", "--deploy-mode", "cluster", "--master", "spark://spark-master:7077", "--packages", "org.apache.spark:spark-sql-kafka-0-10_2.12:3.3.0,org.apache.spark:spark-sql_2.12:3.3.0", "--class", "io.github.malyszaryczlowiek.SparkStreamingAnalyser", "/opt/bitnami/spark/SparkStreamingAnalyser-0.1.0.jar", "100"]

# add this if not work
# ""--conf", "spark.shuffle.service.enabled=false,spark.dynamicAllocation.enabled=false"