package com.github.malyszaryczlowiek
package util

/**
 * Due to Security reasons, any data which must be directly stored in DB,
 * can contain only numbers, letters and dash "-". ANy other char must be rejected.
 */
// @deprecated("Previously used to validate password input.")
object ChatNameValidator {

  // private val regex = "[a-zA-Z-]+".r
  /**
   * Char in name may contain only char characters
   * and whitespaces.
   */
  private val singleCharRegex = "[\\s\\w]".r

  /**
   * Whole chat name must contain at least one character.
   */
  private val fullRegex = "[\\w]+[\\s\\w]*".r

  /**
   * Method check input string if contains forbidden chars
   * returned list is not empty.
   *
   * @param str
   * @return return list of forbidden characters.
   */
  def isValid(str: String): Boolean = fullRegex.matches(str)


}
