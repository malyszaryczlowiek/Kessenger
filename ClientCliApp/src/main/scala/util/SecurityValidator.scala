package com.github.malyszaryczlowiek
package util

/**
 * Due to Security reasons, any data which must be directly stored in DB,
 * can contain only numbers, letters and dash "-". ANy other char must be rejected.
 */
object SecurityValidator {

  val regex = "[0-9a-zA-Z-]+".r

  /**
   * Method check input string if contains forbidden chars
   * returned list is not empty.
   *
   * @param str
   * @return
   */
  def isValid(str: String): List[Char] = //???
    str.toList.filter(c => !regex.matches(c.toString))
}
