package com.github.malyszaryczlowiek
package util


object ChatNameValidator {
  
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
