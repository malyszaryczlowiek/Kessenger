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
 - napisać loggery i skonfigurować je tak aby w dv wyświtlały do poziomu debug a w prod do poziomu WARN
 - poszukać informacji o hashowaniu danych w node/angular a następnie przeimplementować to do backendu.
 - narysować schemat architektury i uzupełnić opis w READ.ME

 */