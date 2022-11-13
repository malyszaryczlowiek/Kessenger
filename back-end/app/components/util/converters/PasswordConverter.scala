package components.util.converters

import com.github.t3hnar.bcrypt
import com.github.t3hnar.bcrypt._

import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.Password

import scala.util.{Failure, Success, Try}


class PasswordConverter {
  def generateSalt: String = bcrypt.generateSalt

  private val defaultSalt = "$2a$10$8K1p/a0dL1LXMIgoEDFrwO"

  def convert (pas: => String, salt: => String = defaultSalt): Either[String, Password] = {
    pas.bcryptSafeBounded(salt) match {
      case Failure(exception) => Left(exception.getMessage)
      case Success(encrypted) => Right(encrypted)
    }
  }


  def validatePassword (pass: String, hash: Password): Try[Boolean] = {
    pass.isBcryptedSafeBounded (hash)
  }


}
