package io.github.malyszaryczlowiek
package runners

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import org.apache.spark.sql.{KeyValueGroupedDataset, Row, SparkSession}
import org.apache.spark.graphx._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.functions.expr
import org.apache.spark.sql.types._

import config.AppConfig.dbConfig
import mappers.Mappers._
//import encoders.GraphEncoders._



class  DbGraphRunner
object DbGraphRunner {

  private val logger: Logger = LogManager.getLogger(classOf[DbGraphRunner])

  def run(): Unit = {

    logger.trace(s"Starting app")

    val sparkSession = SparkSession
      .builder
      .appName("SparkGraphX")
      // two config below added to solve
      // Initial job has not accepted any resources;
      // check your cluster UI to ensure that workers
      // are registered and have sufficient resources
      //      .config("spark.shuffle.service.enabled", "false")
      //      .config("spark.dynamicAllocation.enabled", "false")
      .master("local[2]")
      //  .master("spark://spark-master:7077")    // option for cluster  spark://spark-master:7077
      .getOrCreate()

    val sc = sparkSession.sparkContext

    /*
    vertexy to będą użytkownicy
    edges to będą czaty - połączenia między nimi.
    */


    val usersSchema = StructType(
      StructField("user_id",          StringType, nullable = false)
        :: StructField("user_num_id", LongType,   nullable = false)
        :: Nil)


    val users = sparkSession.read
      .format("jdbc")
      .option("url",      dbConfig.dbUrlWithSchema)
      .option("dbtable",  "users")
      .option("user",     dbConfig.user)
      .option("password", dbConfig.pass)
      //.schema( usersSchema )
      .load()
      .withColumnRenamed("user_id", "uid")

    users.createTempView("users")


    val vertices: RDD[(VertexId, String)] = users.rdd.map( toVertex )


    val usersChatSchema = StructType(
      StructField("user_id", StringType, nullable = false)
        :: StructField("chat_id", StringType, nullable = false)
        :: Nil)


    val usersAndChats = sparkSession.read
      .format("jdbc")
      .option("url",      dbConfig.dbUrlWithSchema)
      .option("dbtable",  "users_chats")
      .option("user",     dbConfig.user)
      .option("password", dbConfig.pass)
      //.schema( usersChatSchema )
      .load()
      .withColumnRenamed("user_id", "ucid")

    usersAndChats.createTempView("users_chats")

    // merging users with userChats to get user_num_id
    val mergedChats = users.join(usersAndChats, expr("""uid = ucid"""))

    mergedChats.toDF().foreach( r => logger.info(s"vertex -> user_num_id: ${r.getAs[VertexId](s"user_num_id")}"))


    // for using syntax: $"column_name"
    import sparkSession.implicits._

    // grouping via chat_id
    val groupedChats2: KeyValueGroupedDataset[String, Row] =
      mergedChats.groupByKey(r => r.getAs[String](s"chat_id"))

    val preEdges = groupedChats2.flatMapGroups((chatId: String, rowIterator: Iterator[Row]) => {
        // i tutaj trzeba po iteratorze wyłuskać wszystkie user_num_id z row'ów
        // i utworzyć listę par z user_num_id
        // następnie tę listę należy flatMappowac aby uzyskać właściwą RDD
        val userNumIds = List.empty[Long]
        while (rowIterator.hasNext) {
          val r = rowIterator.next()
          r.getAs[VertexId](s"user_num_id") :: userNumIds
        }
        val u = userNumIds.distinct
        u.zip(u)
          .filter(t => t._1 != t._2)
          .map(t => {
            if (t._1 < t._2) t
            else (t._2, t._1)
          })
          .distinct
          .map(t => (t._1, t._2, chatId))
      })
      .map(t => Edge(t._1, t._2, t._3))

    preEdges.foreach( e => logger.info(s"edge -> srcVertex: ${e.srcId}, edgeValue: ${e.attr}, destVertex: ${e.dstId}"))


    // tutaj row iterator zawiera informacje o chatId oraz userId
    val edges: RDD[Edge[String]] = preEdges.rdd



    // teraz tworzymy graf
    val graph: Graph[String, String] = Graph(vertices, edges)


    val ops = new GraphOps[String, String](graph)


    val pageRanked  = graph.pageRank(0.01)
    val pageRankRev = graph.reverse.pageRank(0.01).vertices

    val pg = pageRanked.joinVertices(pageRankRev)((vid: VertexId, rank1: Double, rank2: Double) => (rank1 + rank2) / 2)

    pg.vertices.toDS().foreach(
      t => logger.info(s"user_num_id: ${t._1}, pageRank: ${t._2}")
    )

//    pg.vertices
//      .toDS()
//      .foreach(vidAndValue => {
//        val vid   = vidAndValue._1
//        val value = vidAndValue._2
//        // TODO i to pg teraz trzeba zapisać do DB
//
//
//      })



  }


}











