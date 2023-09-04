FROM bitnami/spark:3.3.0
CMD ["./sbin/start-worker.sh", "spark://spark-master:7077"]

