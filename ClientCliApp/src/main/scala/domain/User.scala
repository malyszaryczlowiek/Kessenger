package com.github.malyszaryczlowiek
package domain

import java.util.UUID

case class User(userId: UUID, login: String, pass: String)
