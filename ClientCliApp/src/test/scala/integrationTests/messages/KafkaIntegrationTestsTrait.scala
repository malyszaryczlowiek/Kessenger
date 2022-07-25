package com.github.malyszaryczlowiek
package integrationTests.messages

import kessengerlibrary.domain.{User, Chat}
import kessengerlibrary.kafka.configurators.KafkaTestConfigurator

import messages.{ MessagePrinter, ChatManager, KessengerAdmin}

import integrationTests.IntegrationTestsTrait


import java.util.UUID

import scala.sys.process.*

trait KafkaIntegrationTestsTrait extends munit.FunSuite, IntegrationTestsTrait:
//
//  private var isKafkaBrokerRunning = true
//
//  override def beforeAll(): Unit =
//    super.beforeAll()
//    kafkaBeforeAll()
//
//
//  def kafkaBeforeAll(): Unit =
//    val makeZookeeperStartScriptExecutable = s"chmod +x ${pathToScripts}/startZookeeper".!!
//    println( makeZookeeperStartScriptExecutable )
//
//    val makeZookeeperStopScriptExecutable = s"chmod +x ${pathToScripts}/stopZookeeper".!!
//    println( makeZookeeperStopScriptExecutable )
//
//    val makeKafkaStartScriptExecutable = s"chmod +x ${pathToScripts}/startKafka".!!
//    println( makeKafkaStartScriptExecutable )
//
//    val makeKafkaStopScriptExecutable = s"chmod +x ${pathToScripts}/stopKafka".!!
//    println( makeKafkaStopScriptExecutable )
//
//    val makeCreateTopicScriptExecutable = s"chmod +x ${pathToScripts}/createTopic".!!
//    println( makeCreateTopicScriptExecutable )
//
//
//  /**
//   * Before each test we start fresh kafka broker
//   * @param context
//   */
//  override def beforeEach(context: BeforeEach): Unit =
//    super.beforeEach(context)
//    kafkaBeforeEach()
//
//
//
//  def kafkaBeforeEach(): Unit =
//    isKafkaBrokerRunning = true
//    val outputOfZookeeperStarting = s"./${pathToScripts}/startZookeeper".!!
//    Thread.sleep(50) // wait for zookeeper initialization
//    val outputOfKafkaStarting = s"./${pathToScripts}/startKafka".!!
//    println(s"Created kafka-test-container container")
//    Thread.sleep(50)
//    KessengerAdmin.startAdmin(new KafkaTestConfigurator)
//
//
//
//  /**
//   * Closing kafka broker after each test.
//   * @param context
//   */
//  override def afterEach(context: AfterEach): Unit =
//    kafkaAfterEach()
//    super.afterEach(context)
//
//
//  def kafkaAfterEach(): Unit =
//    KessengerAdmin.closeAdmin()
//    if isKafkaBrokerRunning then
//      switchOffKafkaBroker()
//    Thread.sleep(50)
//    val outputOfZookeeperStopping = s"./${pathToScripts}/stopZookeeper".!!
//    val names = outputOfZookeeperStopping.split('\n')
//    println( s"Stopped ${names(0)} container\nDeleted ${names(1)} container\nDeleted \'${names(2)}\' docker testing network" )
//
//
//
//  /**
//   * closing zookeeper and removing local docker testing network
//   * after all tests.
//   */
//  override def afterAll(): Unit = super.afterAll()
//
//
//  def switchOffKafkaBroker(): Unit =
//    val outputOfKafkaStopping = s"./${pathToScripts}/stopKafka".!!
//    val name = outputOfKafkaStopping.split('\n')
//    println( s"Stopped ${name(0)} container\nDeleted ${name(1)} container" )
//    isKafkaBrokerRunning = false

  
  
end KafkaIntegrationTestsTrait