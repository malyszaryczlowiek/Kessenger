package util

import io.github.malyszaryczlowiek.kessengerlibrary.domain.SessionInfo

import java.util.UUID

class HeadersParser {

  def parseKSID(ksid: String): Option[SessionInfo] = {
    val splitted = ksid.split("__")
    if (splitted.length == 3) {
      try {
        val sessionId = UUID.fromString( splitted(0))
        val userId = UUID.fromString( splitted(1))
        val validityTime = splitted(2).toLong
        Option( SessionInfo(sessionId , userId , validityTime ) )
      } catch {
        case e: _ => None
      }
    } else None
  }

}
