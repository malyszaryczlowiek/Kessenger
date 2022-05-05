package com.github.malyszaryczlowiek
package domain

import com.github.malyszaryczlowiek.domain.Domain.{UserID, Login}

import java.util.UUID

case class User(userId: UserID, login: Login, salt: Option[String] = None, joiningOffset: Long = 0L)
