package io.github.malyszaryczlowiek
package unitTests.messages

import kessengerlibrary.domain.{Chat, User}
import messages.MessagePrinter
import messages.MessagePrinter.messagePrinterReverseOrdering

import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.{Offset, Partition}
import io.github.malyszaryczlowiek.kessengerlibrary.kafka.configurators.KafkaConfigurator

import java.time.{LocalDateTime, Month}
import java.util.UUID

class MessagePrinterTests extends munit.FunSuite:


  test("Testing ordering of MessagePrinter") {

    val chat1 = Chat("fakeID", "fakeName",false, LocalDateTime.of(2022, Month.JULY, 18, 18, 18, 18))
    val chat2 = Chat("fakeID", "fakeName",false, LocalDateTime.of(2022, Month.JULY, 18, 18, 18, 20))

    val user = User(UUID.randomUUID(), "fakeuser")
    val offsets = (0 until KafkaConfigurator.configurator.CHAT_TOPIC_PARTITIONS_NUMBER)
      .map(i => (i, 0L)).toMap[Partition, Offset]

    val printer1 = new MessagePrinter(user, chat1, offsets)
    val printer2 = new MessagePrinter(user, chat2, offsets)


    val value1 = messagePrinterReverseOrdering.compare(printer2, printer1)
    println(s"value = $value1")

    assert( value1 < 0 ,s"value is =< 0" )
  }

end MessagePrinterTests

