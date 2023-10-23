package util

import com.github.t3hnar.bcrypt
import com.github.t3hnar.bcrypt._
import io.github.malyszaryczlowiek.kessengerlibrary.domain.Domain.Password

import scala.util.{Failure, Success, Try}
import play.api.{ConfigLoader, Configuration}

import javax.inject.Inject


class PasswordConverter @Inject() (conf: Configuration) {
  def generateSalt: String = bcrypt.generateSalt

  private val defaultSalt = conf.get("kessenger.util.passwordconverter.salt")(ConfigLoader.stringLoader)

  def convert (pas: => String, salt: => String = defaultSalt): Either[String, Password] = {
    pas.bcryptSafeBounded(salt) match {
      case Failure(exception) => Left(exception.getMessage)
      case Success(encrypted) =>
        // here we cut off salt from hash
        Right(encrypted.drop(defaultSalt.length))
    }
  }


  def validatePassword (pass: String, hash: Password): Try[Boolean] = {
    pass.isBcryptedSafeBounded (hash)
  }


}
