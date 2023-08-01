package io.github.malyszaryczlowiek
package db



case class DbTable(tableName: String, columns: Map[String, String]) {


  def getTableColumnsWithTypes: String = {
    val s = columns.map(t => s"${t._1} ${t._2}").reduceLeft((s:String, kv: String) => s"$s , $kv")
    val shortS = s.substring(0, s.length - 2)
    s"( $shortS )"
  }


  def getTableColumnsNames: String = {
    val s = columns.keys.reduceLeft((s, t) => s"$s , $t")
    val shortS = s.substring(0, s.length - 2)
    s"( $shortS )"
  }


  def getQuestionMarks: String = {
    // todo napisać testy i potem ponownie spróbować uruchomić aplikację.
    val s = columns.keys.reduceLeft((s, t) => s"? , ")
    val shortS = s.substring(0, s.length - 3)
    s"( $shortS )"
  }


}
