package com.github.malyszaryczlowiek
package integrationTests.messages

import sys.process.*


/**
 * Watch out!!!!
 * Running integration tests with restarting kafka broker before every test
 * are extremely slow.
 *
 */
class ChatManagerTests extends munit.FunSuite {

  val pathToScripts = "./src/test/scala/integrationTests/messages"
  val waitingTimeMS = 5000

  override def beforeAll(): Unit =
    val makeZookeeperStartScriptExecutable = s"chmod +x ${pathToScripts}/startZookeeper".!!
    println( makeZookeeperStartScriptExecutable )

    val makeZookeeperStopScriptExecutable = s"chmod +x ${pathToScripts}/stopZookeeper".!!
    println( makeZookeeperStopScriptExecutable )

    val makeKafkaStartScriptExecutable = s"chmod +x ${pathToScripts}/startKafka".!!
    println( makeKafkaStartScriptExecutable )

    val makeKafkaStopScriptExecutable = s"chmod +x ${pathToScripts}/stopKafka".!!
    println( makeKafkaStopScriptExecutable )

    val makeCreateTopicScriptExecutable = s"chmod +x ${pathToScripts}/createTopic".!!
    println( makeCreateTopicScriptExecutable )
    
    val outputOfZookeeperStarting = s"./${pathToScripts}/startZookeeper".!!
    Thread.sleep(5_000) // wait for zookeeper initialization
    super.beforeAll()

  /**
   * Before each test we start fresh kafka broker
   * @param context
   */
  override def beforeEach(context: BeforeEach): Unit =
    val outputOfKafkaStarting = s"./${pathToScripts}/startKafka".!!
    println(s"Created kafka-test-container container")
    Thread.sleep(1_000)
    super.beforeEach(context)


  /**
   * Closing kafka broker after each test.
   * @param context
   */
  override def afterEach(context: AfterEach): Unit =
    val outputOfKafkaStopping = s"./${pathToScripts}/stopKafka".!!
    val name = outputOfKafkaStopping.split('\n')
    println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
    Thread.sleep(2_000)
    super.afterEach(context)

  /**
   * closing zookeeper and removing local docker testing network
   * after all tests.
   */
  override def afterAll(): Unit =
    val outputOfZookeeperStopping = s"./${pathToScripts}/stopZookeeper".!!
    val names = outputOfZookeeperStopping.split('\n')
    println( s"Stopped ${names(0)} container\nDeleted ${names(1)} container\nDeleted \'${names(2)}\' docker testing network" )
    super.afterAll()

  private def createTopic(topicName: String): Unit =
    val createTopic = s"./${pathToScripts}/createTopic $topicName".!!
    println( createTopic )




  test("Testing topic creation script") {
    createTopic("foo")
    assert(1 == 1, "failed one equality")
  }

  test("Testing with another topic") {
    createTopic("bar")
    assert(1 == 1, "failed one equality")
  }

}
