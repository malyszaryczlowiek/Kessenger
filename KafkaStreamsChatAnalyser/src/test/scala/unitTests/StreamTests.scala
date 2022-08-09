package com.github.malyszaryczlowiek
package unitTests

import kessengerlibrary.domain.User
import kessengerlibrary.messages.Message
import kessengerlibrary.serdes.{MessageSerde, UserSerde}

import org.apache.kafka.streams.kstream.Suppressed.BufferConfig.unbounded
import org.apache.kafka.streams.kstream.internals.SessionWindow
import org.apache.kafka.streams.{KeyValue, TestInputTopic, TestOutputTopic, Topology, TopologyTestDriver}
import org.apache.kafka.streams.kstream.{Grouped, Named, Suppressed, TimeWindows, Windowed, Windows}
import org.apache.kafka.streams.scala.{ByteArrayWindowStore, StreamsBuilder}
import org.apache.kafka.streams.scala.kstream.{Consumed, KGroupedStream, KStream, KTable, Materialized, Produced}
import org.apache.kafka.streams.scala.serialization.Serdes.{longSerde, stringSerde}
import org.apache.kafka.streams.state.{KeyValueStore, WindowBytesStoreSupplier, WindowStore}

import java.time.ZoneId
import java.util.UUID



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
  private var messagesPerZone: TestOutputTopic[String, Long] = _
  private var windowedTopic:   TestOutputTopic[Windowed[String], Long] = _
  private var testDriver:      TopologyTestDriver            = _
  private var store:           KeyValueStore[String,Long]    = _



  override def beforeAll(): Unit =
    super.beforeAll()
  end beforeAll



  override def beforeEach(context: BeforeEach): Unit =
    super.beforeEach(context)
  end beforeEach



  override def afterEach(context: AfterEach): Unit =
    testDriver.close()
    super.afterEach(context)


  /**
   * Note!!!
   * windowing does not work with TopologyTestDriver
   *
   */
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

      .windowedBy( TimeWindows.ofSizeWithNoGrace(java.time.Duration.ofSeconds(10)) )

      .count()(Materialized.as("messages-per-zone-within-one-hour")(stringSerde, longSerde))



    val streamToSave: KStream[String, Long] = lastFiveMinutes.toStream((windowed, long) => windowed.key())


    // and finally we save to topic
    streamToSave.to("output-topic")(Produced.`with`(stringSerde, longSerde))

    // we build topology
    topology = builder.build()


    testDriver       = new TopologyTestDriver(topology)
    inputTopic       = testDriver.createInputTopic("input-topic", userSerde.serializer, messageSerde.serializer)
    messagesPerZone  = testDriver.createOutputTopic("output-topic", stringSerde.deserializer, longSerde.deserializer)


    //testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(1))



    val uuid1 = UUID.randomUUID()
    val uuid2 = UUID.randomUUID()

    // create test driver
    val user1 = User(uuid1, "User1")
    val user2 = User(uuid2, "User1")

    val message1 = Message("", user1.userId, 0L, ZoneId.of("Europe/Warsaw"), "", "", false)
    val message2 = Message("", user1.userId, 0L, ZoneId.of("Europe/Paris"), "", "", false)
    val message3 = Message("", user1.userId, 0L, ZoneId.of("Europe/Paris"), "", "", false)


    inputTopic.pipeInput(user1, message1)
    inputTopic.pipeInput(user2, message2)
    inputTopic.pipeInput(user2, message2)

    val map = messagesPerZone.readKeyValuesToMap()

    assert( map.get("Europe/Warsaw").equals(1L) )
    assert( map.get("Europe/Paris").equals(2L) )


    Thread.sleep(1200)

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

    val message1 = Message("", user1.userId, 0L, ZoneId.of("Europe/Warsaw"), "", "", false)
    val message2 = Message("", user1.userId, 0L, ZoneId.of("Europe/Paris"), "", "", false)
    val message3 = Message("", user1.userId, 0L, ZoneId.of("Europe/Paris"), "", "", false)


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











































  test("test") {



//    inputTopic.pipeInput("key", 42L)
//    assert(outputTopic.readKeyValue.equals(new KeyValue[String, Long]("key", 42L)))
//    assert(outputTopic.isEmpty)

//    testDriver.advanceWallClockTime(java.time.Duration.ofSeconds(20))
//    val store: KeyValueStore[String, Long] = testDriver.getKeyValueStore("store-name")
  }




}
