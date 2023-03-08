package conf

trait KafkaConf {

  def BOOTSTRAP_SERVERS: String

  def CHAT_TOPIC_REPLICATION_FACTOR: Short
  def CHAT_TOPIC_PARTITIONS_NUMBER: Int
  def CHAT_TOPIC_MESSAGE_SERIALIZER: String
  def CHAT_TOPIC_MESSAGE_DESERIALIZER: String

  def JOINING_TOPIC_REPLICATION_FACTOR: Short
  def JOINING_TOPIC_PARTITIONS_NUMBER: Int
  def JOINING_TOPIC_INVITATION_SERIALIZER: String
  def JOINING_TOPIC_INVITATION_DESERIALIZER: String


  def WRITING_TOPIC_REPLICATION_FACTOR: Short
  def WRITING_TOPIC_PARTITIONS_NUMBER: Int
  def WRITING_TOPIC_WRITING_SERIALIZER: String
  def WRITING_TOPIC_WRITING_DESERIALIZER: String


}

/*
TODO
 1. skonfigurować logger zarówno dla prod jak i dev.
 2. przetestować czy Controller może być oznaczony jako @Singleton
 3. we frontendzie po wejściu w ustawienia sprawdzić dlaczego nie ma stref czasowych
 4. przygotować wersję produkcyjną fronendu.
 5. napiasć skrypt uruchamiający beckend jako: run -Dhttp.port=9000 tak aby można było uruchomić kilka jako dev mode.
 */