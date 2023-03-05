FROM bitnami/kafka:3.1
# CMD ["./opt/bitnami/kafka/bin/kafka-topics.sh", "--bootstrap-server", "zookeeper:2181", "--create", "--topic", "spark-output", "--if-not-exists", "--partitions", "3", "--replication-factor", "3"]
#CMD ["./bin/kafka-topics.sh", "--create", "--topic", "all-messages", "--if-not-exists", "--partitions", "1", "--replication-factor", "3"]
# /opt/bitnami/kafka/bin/kafka-topics.sh to run

# to list all kafka topics
# ./opt/bitnami/kafka/bin/kafka-topics.sh --list --zookeeper zookeper:2181



#  does not work in any variant
# ./opt/bitnami/kafka/bin/kafka-topics.sh \
#   --bootstrap-server localhost:9093 \
#   --topic spark-output \
#   --create \
#   --if-not-exists \
#   --partitions 3 \
#   --replication-factor 3