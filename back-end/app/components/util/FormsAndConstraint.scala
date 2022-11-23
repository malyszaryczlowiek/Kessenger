package components.util

import components.util.LoginCredentials.{LoginCredentialParser, LoginCredentials}
import models.SignupData

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

@deprecated
class FormsAndConstraint {

//
//
//  private val loginRegex = "[\\p{Alnum}\\p{Punct}]*[\\p{Punct}]+[\\p{Alnum}\\p{Punct}]*|[0-9]+".r
//  private val loginLength = "[\\p{Alnum}\\p{Punct}]{0,3}".r
//
//
//  private val loginError =
//    """Login must not contain punctation characters:
//      |!#%&'()*+,-./:;<=>?@[\\]^_`{|}~.\"$
//      |and must not contain only numbers.""".stripMargin
//
//
//  val loginCheckConstraint: Constraint[String] = Constraint("constraints.logincheck")({ plainText =>
//    val errors = plainText match {
//      case loginRegex() => Seq(ValidationError(loginError))
//      case loginLength() => Seq(ValidationError("Login must contain at least four characters."))
//      case _ => Nil
//    }
//    if (errors.isEmpty) {
//      Valid
//    } else {
//      Invalid(errors)
//    }
//  })
//
//
//  private val length = "[\\p{Alnum}\\p{Punct}]{3,}".r
//
//
//  private val passError =
//    """Password must be at least 8 character long,
//      |must contain at least one capital letter,
//      |must not contain white spaces
//      |and must contain at least one punctation character:
//      |!#%&'()*+,-./\:;<=>?@[]^_`{|}~."$""".stripMargin
//
//
//  val passwordCheckConstraint: Constraint[String] = Constraint("constraints.passwordcheck")({ plainText =>
//    val errors = plainText match {
//      case length() => Nil // at least 3 characters
//      case _ => Seq(ValidationError("Password must contain at least three characters."))
//    }
//    if (errors.isEmpty) {
//      Valid
//    } else {
//      Invalid(errors)
//    }
//  })
//
//
//  val signupForm: Form[SignupData] = Form(
//    mapping(
//      "login" -> text.verifying(loginCheckConstraint),
//      "pass"  -> nonEmptyText.verifying(passwordCheckConstraint)
//    )(SignupData.apply)(SignupData.unapply)
//  )
//
//  val logForm: Form[LoginCredentials] = Form(
//    mapping(
//      "login" -> nonEmptyText.verifying(loginCheckConstraint),
//      "pass" -> nonEmptyText.verifying(passwordCheckConstraint),
//      "userId" -> text
//    )(LoginCredentials.apply)(LoginCredentials.unapply)
//  )

}
