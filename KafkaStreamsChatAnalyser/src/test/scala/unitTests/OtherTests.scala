package io.github.malyszaryczlowiek
package unitTests

import kessengerlibrary.domain.Domain

import java.util.UUID
import java.util.regex.Pattern

class OtherTests extends munit.FunSuite {

  test("Test topic regex matching") {

    val chatName = Domain.generateChatId(UUID.randomUUID(), UUID.randomUUID())

    val result = Pattern.matches(s"chat-[\\p{Alnum}-]*", chatName)

    assert(result, s"not match")

  }

}
