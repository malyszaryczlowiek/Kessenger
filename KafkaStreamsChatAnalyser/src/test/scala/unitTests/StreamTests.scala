package io.github.malyszaryczlowiek
package unitTests

import kessengerlibrary.model.{Message, User}
import kessengerlibrary.serdes.message.MessageSerde
import kessengerlibrary.serdes.user.UserSerde

import org.apache.kafka.streams.kstream.{Grouped, Named, TimeWindows, Windowed}
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.kstream._
import org.apache.kafka.streams.scala.serialization.Serdes.{intSerde, longSerde, shortSerde, stringSerde}
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams._

import java.time.{Instant, ZoneId}
import java.time.{Duration => jDuration}
import java.util.{Properties, UUID}



class StreamTests extends munit.FunSuite {

  /*

  Documentation for KafkaStreams testing
  https://kafka.apache.org/documentation/streams/developer-guide/testing.html
  https://kafka.apache.org/32/javadoc/org/apache/kafka/streams/test/package-summary.html
  https://kafka.apache.org/32/javadoc/org/apache/kafka/streams/package-summary.html

  */

  private val userSerde    = new UserSerde
  private val messageSerde = new MessageSerde

  private var topology:        Topology                      = _
  private var inputTopic:      TestInputTopic[User, Message] = _

  // output topics
  private var messagesPerZone:   TestOutputTopic[String, Long] = _
  private var numOfUserMessages: TestOutputTopic[User,   Long] = _
  private var outputTopic:       TestOutputTopic[String, Long] = _




  private var testDriver:      TopologyTestDriver            = _
  private var store:           KeyValueStore[String,Long]    = _



  override def beforeAll(): Unit = {
    super.beforeAll()
  }





  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
  }





  override def afterEach(context: AfterEach): Unit = {
    if (testDriver != null) testDriver.close()
    super.afterEach(context)
  }






















  test("count messages per timezone") {



    // define builder
    val builder: StreamsBuilder = new StreamsBuilder()


    // we read from one chat topic
    val inputStream: KStream[User, Message] = builder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))


    val grouped = Grouped.`with`("repartitioned", stringSerde, messageSerde)


    // we repartition and group by zone id
    val groupedStream: KGroupedStream[String, Message] =
      inputStream.groupBy((user, message) => message.zoneId.getId)(grouped)

    // we collect only from last 5 seconds
    val lastFiveMinutes: KTable[Windowed[String], Long] = groupedStream
      //val lastFiveMinutes: KTable[String, Long] = groupedStream

      .windowedBy( TimeWindows.ofSizeWithNoGrace(java.time.Duration.ofSeconds(1)) )

      //.count()(Materialized.as("messages-per-zone-within-one-hour")(stringSerde, longSerde))
      .aggregate( 0L)((string: String, message: Message, value: Long) => value + 1L )(
        Materialized.as("messages-per-zone-within-one-hour")(stringSerde, longSerde)
      )

    //.suppress(Suppressed.untilWindowCloses(unbounded()))

    val streamToSave: KStream[String, Long] = lastFiveMinutes.toStream((windowed, long) => windowed.key())
    // lastFiveMinutes.toStream//((windowed, long) => windowed.key())

    // and finally we save to topic
    streamToSave.to("output-topic")(Produced.`with`(stringSerde, longSerde))

    // we build topology
    topology = builder.build()


    testDriver       = new TopologyTestDriver(topology)
    inputTopic       = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    messagesPerZone  = testDriver.createOutputTopic("output-topic", stringSerde.deserializer, longSerde.deserializer)



    testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(1))



    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    // create test driver
    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User1")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L,  ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)


    inputTopic.pipeInput(user1, message1)
    inputTopic.pipeInput(user2, message2)
    inputTopic.pipeInput(user2, message2)

    val map = messagesPerZone.readKeyValuesToMap()

    assert( map.get("Europe/Warsaw").equals(1L) )
    assert( map.get("Europe/Paris").equals(2L) )


    Thread.sleep(3300)

    inputTopic.pipeInput(user1, message2)
    //val list = messagesPerZone.readKeyValuesToList()

    //    assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Warsaw", 1L)))
    //    assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Paris", 1L)))
    //    assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Paris", 2L)))
    assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Paris", 3L)))


    //    val size = list.size()
    //
    //    println(s"size = $size")
    //
    //    assert( list.get(0).equals(new KeyValue[String, Long]("Europe/Paris", 3L)) )


    // assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Warsaw", 1L)))
    // assert(messagesPerZone.readKeyValue.equals(new KeyValue[String, Long]("Europe/Paris", 2L)))


  }



  test("messages per user") {

    val builder: StreamsBuilder = new StreamsBuilder()

    val inputStream: KStream[User, Message] = builder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))

    val grouping = Grouped.`with`("grouped-by-user", userSerde, messageSerde)

    val groupedKStream: KGroupedStream[User, Message] =  inputStream.groupBy((u,m) => u)(grouping)

    val numOfUserMessagesIn2s: KTable[Windowed[User], Long] = groupedKStream
      .windowedBy( TimeWindows.ofSizeWithNoGrace( java.time.Duration.ofSeconds(2L)))
      .count()(Materialized.as("messages-per-user-within-2s")(userSerde, longSerde))

    val outputStream: KStream[User, Long] = numOfUserMessagesIn2s.toStream((windowed, long) => windowed.key())
    outputStream.to("output-topic")(Produced.`with`(userSerde, longSerde))

    topology = builder.build()

    testDriver = new TopologyTestDriver(topology)
    inputTopic = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    numOfUserMessages = testDriver.createOutputTopic("output-topic", userSerde.deserializer, longSerde.deserializer)


    // testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(1))

    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    // create test driver
    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User1")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1)
    inputTopic.pipeInput(user2, message2)
    inputTopic.pipeInput(user2, message3, instant.plusSeconds(5L))


    val map = numOfUserMessages.readKeyValuesToMap()

    assert(map.get(user1).equals(1L))
    assert(map.get(user2).equals(1L))



//    inputTopic.pipeInput(user1, message2)
//
//    val map2 = numOfUserMessages.readKeyValuesToMap()
//
//    assert(map2.get(user1).equals(2L))



    // assert(numOfUserMessages.readKeyValue.equals(new KeyValue[User, Long](user1, 3L)))


  }


  test("messages per user 2") {

    val builder: StreamsBuilder = new StreamsBuilder()

    val inputStream: KStream[User, Message] = builder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))

    val grouping = Grouped.`with`("grouped-by-user", userSerde, messageSerde)

    // grouping by user
    val groupedKStream: KGroupedStream[User, Message] = inputStream.groupBy((u, m) => u)(grouping)

    val numOfUserMessagesIn2s: KTable[Windowed[User], Long] = groupedKStream
      .windowedBy(TimeWindows.ofSizeAndGrace(java.time.Duration.ofSeconds(2L), java.time.Duration.ofSeconds(5L)))
      .count()(Materialized.as("messages-per-user-within-2s")(userSerde, longSerde))

    numOfUserMessagesIn2s.toStream((windowed, long) => windowed.key())
      .to("output-topic")(Produced.`with`(userSerde, longSerde))

    topology = builder.build()

    testDriver = new TopologyTestDriver(topology)
    inputTopic = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    numOfUserMessages = testDriver.createOutputTopic("output-topic", userSerde.deserializer, longSerde.deserializer)


    // testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(1))

    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    // create test driver
    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User1")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1, instant)
    inputTopic.pipeInput(user2, message2, instant)
    inputTopic.pipeInput(user2, message3, instant.plusSeconds(3L))


    val map = numOfUserMessages.readKeyValuesToMap()

    assert(map.get(user1).equals(1L))
    assert(map.get(user2).equals(2L))



    //    inputTopic.pipeInput(user1, message2)
    //
    //    val map2 = numOfUserMessages.readKeyValuesToMap()
    //
    //    assert(map2.get(user1).equals(2L))


    // assert(numOfUserMessages.readKeyValue.equals(new KeyValue[User, Long](user1, 3L)))


  }


  test("messages per user 3") {

    val builder: StreamsBuilder = new StreamsBuilder()

    val inputStream: KStream[User, Message] = builder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))

    val grouping = Grouped.`with`("grouped-by-user", userSerde, messageSerde)

    // grouping by user
    // todo groupByKey
    val groupedKStream: KGroupedStream[User, Message] = inputStream.groupBy((u, m) => u)(grouping)


    val numOfUserMessagesIn2s: KTable[Windowed[User], Long] = groupedKStream
      .windowedBy(TimeWindows.ofSizeAndGrace(java.time.Duration.ofSeconds(2L), java.time.Duration.ofSeconds(6L)))
      .count()(Materialized.as("messages-per-user-within-2s")(userSerde, longSerde))


    val outputStream: KStream[User, Long] = numOfUserMessagesIn2s.toStream((windowed, long) => windowed.key())
    outputStream.to("output-topic")(Produced.`with`(userSerde, longSerde))

    topology   = builder.build()
    testDriver = new TopologyTestDriver(topology)
    inputTopic = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    numOfUserMessages = testDriver.createOutputTopic("output-topic", userSerde.deserializer, longSerde.deserializer)


    // testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(1))

    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    // create test driver
    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User2")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1, instant)
    inputTopic.pipeInput(user2, message2, instant)
    inputTopic.pipeInput(user2, message3, instant.plusSeconds(3L))// .plusMillis(500L))
    inputTopic.pipeInput(user2, message3, instant.plusSeconds(3L))
    inputTopic.pipeInput(user1, message1, instant.plusSeconds(3L))


    //val map = numOfUserMessages.readKeyValuesToMap()

//    assert(map.get(user1).equals(1L))
//    assert(map.get(user2).equals(2L))
    // println(s"List size: ${numOfUserMessages.readKeyValuesToList().size()}")

    numOfUserMessages.readKeyValuesToList().forEach( c => println(s"key: ${c.key},\t\tvalue: ${c.value}"))
    println(s"################################")
    inputTopic.pipeInput(user1, message1, instant.plusMillis(0L))
    numOfUserMessages.readKeyValuesToList().forEach( c => println(s"key: ${c.key},\t\tvalue: ${c.value}"))
  }



  // TODO proper tests



  /**
   * "number of all messages per time unit (2s)."
   */
  test("number of all messages per time unit (2s).") {

    val streamBuilder: StreamsBuilder = new StreamsBuilder()

    val outputStream = streamBuilder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))
      .mapValues(mess => 0.shortValue)
      .groupBy((k,v) => 1)(Grouped.as("g1").withKeySerde(intSerde).withValueSerde(shortSerde))
      .windowedBy( TimeWindows.ofSizeAndGrace( jDuration.ofSeconds(2L), jDuration.ofMillis(250L)))
      .count()(Materialized.`with`(intSerde, longSerde))
      .toStream( (windowed, num) => windowed.window().startTime().toString)

    outputStream.to("output-topic")(Produced.`with`(stringSerde, longSerde))

    topology    = streamBuilder.build()
    testDriver  = new TopologyTestDriver(topology)
    inputTopic  = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    outputTopic = testDriver.createOutputTopic("output-topic", stringSerde.deserializer(), longSerde.deserializer)


    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User2")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1, instant)
    inputTopic.pipeInput(user2, message2, instant)
    inputTopic.pipeInput(user2, message3, instant.plusMillis(390L)) // .plusMillis(500L))
    inputTopic.pipeInput(user2, message3, instant.plusMillis(350L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L).plusSeconds(10L))

    outputTopic.readKeyValuesToList().forEach( c => println(s"key: ${c.key},\t\tvalue: ${c.value}"))
  }





  /**
   * "number of all messages per time unit (2s) per zone"
   */
  test("number of all messages per time unit (2s) per zone") {

    val streamBuilder: StreamsBuilder = new StreamsBuilder()

    val outputStream = streamBuilder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))
      .map((user,message) => (message.zoneId.getId, 0.toShort))
      .groupByKey(Grouped.as("g1").withKeySerde(stringSerde).withValueSerde(shortSerde))
      .windowedBy(TimeWindows.ofSizeAndGrace(jDuration.ofSeconds(2L), jDuration.ofMillis(250L)))
      .count()(Materialized.`with`(stringSerde, longSerde))
      .toStream(
        (windowed, num) => s"${windowed.window().startTime().toString}___${windowed.key()}"
      )

    outputStream.to("output-topic")(Produced.`with`(stringSerde, longSerde))

    topology    = streamBuilder.build()
    testDriver  = new TopologyTestDriver(topology)
    inputTopic  = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    outputTopic = testDriver.createOutputTopic("output-topic", stringSerde.deserializer(), longSerde.deserializer)


    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User2")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1, instant)
    inputTopic.pipeInput(user2, message2, instant)
    inputTopic.pipeInput(user2, message3, instant.plusMillis(390L)) // .plusMillis(500L))
    inputTopic.pipeInput(user2, message3, instant.plusMillis(350L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L).plusSeconds(10L))

    outputTopic.readKeyValuesToList().forEach(c => println(s"key: ${c.key},\t\tvalue: ${c.value}"))
  }





  /**
   * "number of all messages per time unit (2s) per zone"
   */
  test("number of all messages per time unit (2s) per user") {

    val streamBuilder: StreamsBuilder = new StreamsBuilder()

    val outputStream = streamBuilder.stream("input-topic")(Consumed.`with`(userSerde, messageSerde))
      //.map((user, message) => (message.zoneId.getId, 0.toShort))
      .groupByKey(Grouped.as("g1").withKeySerde(userSerde).withValueSerde(messageSerde))
      .windowedBy(TimeWindows.ofSizeAndGrace(jDuration.ofSeconds(2L), jDuration.ofMillis(250L)))
      .count()(Materialized.`with`(userSerde, longSerde))
      .toStream(
        (windowed, num) => s"${windowed.window().startTime().toString}___${windowed.key().userId.toString}"
      )

    outputStream.to("output-topic")(Produced.`with`(stringSerde, longSerde))

    topology = streamBuilder.build()
    testDriver = new TopologyTestDriver(topology)
    inputTopic = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    outputTopic = testDriver.createOutputTopic("output-topic", stringSerde.deserializer(), longSerde.deserializer)


    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User2")

    val message1 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Warsaw"), "", "", false, None)
    val message2 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)
    val message3 = Message("", user1.userId, user1.login, 0L, 0L, ZoneId.of("Europe/Paris"), "", "", false, None)

    val instant = Instant.now()

    inputTopic.pipeInput(user1, message1, instant)
    inputTopic.pipeInput(user2, message2, instant)
    inputTopic.pipeInput(user2, message3, instant.plusMillis(390L)) // .plusMillis(500L))
    inputTopic.pipeInput(user2, message3, instant.plusMillis(350L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L))
    inputTopic.pipeInput(user1, message1, instant.plusMillis(30L).plusSeconds(10L))

    outputTopic.readKeyValuesToList().forEach(c => println(s"key: ${c.key},\t\tvalue: ${c.value}"))
  }


}

