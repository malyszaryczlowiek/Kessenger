package io.github.malyszaryczlowiek
package db

case class DbTable(tableName: String, columns: Map[String, String]) {



  def getTableColumnsWithTypes: String = {
    implement
    ""
  }



  def getTableColumnsNames: String = {
    implement
    ""
  }



}
