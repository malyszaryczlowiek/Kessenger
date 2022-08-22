package com.github.malyszaryczlowiek
package serde

import org.apache.spark.sql.Encoder
import org.apache.spark.sql.types.{BooleanType, LongType, StringType, StructField, StructType}

class CustomEncoder extends Encoder[(Long, String, String, String, String, String, Boolean, String, Long)] {

  override def schema: StructType = StructType(
    StructField("timestamp",      LongType, nullable = false) ::
      StructField("chat_id",      StringType, nullable = false) ::
      StructField("chat_name",    StringType, nullable = false) ::
      StructField("content",      StringType, nullable = false) ::
      StructField("author_id",    StringType, nullable = false) ::
      StructField("author_login", StringType, nullable = false) ::
      StructField("group_chat",   BooleanType, nullable = false) ::
      StructField("zone_id",      StringType, nullable = false) ::
      StructField("sending_time", LongType, nullable = false) ::
      Nil
  )

  override def clsTag: _root_.scala.reflect.ClassTag[(Long, _root_.scala.Predef.String, _root_.scala.Predef.String, _root_.scala.Predef.String, _root_.scala.Predef.String, _root_.scala.Predef.String, Boolean, _root_.scala.Predef.String, Long)] = ???
}
