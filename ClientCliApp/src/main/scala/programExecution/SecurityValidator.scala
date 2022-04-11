package com.github.malyszaryczlowiek
package programExecution


/**
* Due to Security reasons, any data which must be stored in DB can contain only
 * numbers, letters and dash "-". ANy other char must be rejected.
*/
object SecurityValidator {

  /**
   * Method check input string if contains forbidden chars
   * returned list is not empty.
   * @param str
   * @return
   */
  def isValid(str: String): List[Char] = ???
}
