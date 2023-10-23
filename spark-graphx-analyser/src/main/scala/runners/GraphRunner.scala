package io.github.malyszaryczlowiek
package runners

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import org.apache.spark.sql.SparkSession

import org.apache.spark._
import org.apache.spark.graphx._ // graphx._
// To make some of the examples work we will also need RDD
import org.apache.spark.rdd.RDD



class GraphRunner  {

  private val logger: Logger = LogManager.getLogger(classOf[GraphRunner])

  def run(): Unit  = {

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

    val users: RDD[(VertexId, (String, String))] =
      sc.parallelize(Seq((3L, ("rxin", "student")), (7L, ("jgonzal", "postdoc")),
        (5L, ("franklin", "prof")), (2L, ("istoica", "prof"))))
    // Create an RDD for edges
    val relationships: RDD[Edge[String]] =
      sc.parallelize(Seq(Edge(3L, 7L, "collab"), Edge(5L, 3L, "advisor"),
        Edge(2L, 5L, "colleague"), Edge(5L, 7L, "pi")))
    // Define a default user in case there are relationship with missing user
    val defaultUser = ("John Doe", "Missing")
    // Build the initial Graph
    val graph = Graph(users, relationships, defaultUser)

    // Count all users which are postdocs
    graph.vertices.filter { case (id, (name, pos)) => pos == "postdoc" }.count
    // Count all the edges where src > dst
    graph.edges.filter(e => e.srcId > e.dstId).count

    // Use the triplets view to create an RDD of facts.
    val facts: RDD[String] =
      graph.triplets.map(triplet =>
        triplet.srcAttr._1 + " is the " + triplet.attr + " of " + triplet.dstAttr._1)
    facts.collect.foreach(logger.trace)

  }


}
