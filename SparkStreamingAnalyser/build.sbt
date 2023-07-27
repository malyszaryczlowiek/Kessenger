ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.12.16"

lazy val root = (project in file("."))
  .settings(
    name := "SparkStreamingAnalyser",
    idePackagePrefix := Some("io.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",

    libraryDependencies ++= Seq(

      // Own library with util and domain classes.
      // https://github.com/malyszaryczlowiek/kessenger-lib
      "io.github.malyszaryczlowiek" %% "kessenger-lib" % "0.3.27",



      "org.apache.spark" %% "spark-sql"            % "3.3.0" % "provided",
      "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.3.0",  //% "provided",


      // for creating kafka topic
      "org.apache.kafka"  % "kafka-clients"        % "3.1.0",
      "org.apache.kafka"  % "kafka-streams"        % "3.1.0",

      // slf4j
      "org.slf4j" % "slf4j-nop" % "2.0.5",

      // log4j logger
      "org.apache.logging.log4j" % "log4j-api" % "2.20.0",
      "org.apache.logging.log4j" % "log4j-core" % "2.20.0",


      // https://github.com/lightbend/config
      "com.typesafe" % "config" % "1.4.2",

      // for deserialization data from kafka
      "io.circe" %% "circe-core"    % "0.14.2",
      "io.circe" %% "circe-generic" % "0.14.2",
      "io.circe" %% "circe-parser"  % "0.14.2",

      // for tests
      "org.scalameta" %% "munit"            % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,


      // to solve transitive dependency error with spark and cats
      "org.scalanlp" %% "breeze" % "2.1.0"
    )
  )

// for build JAR executable.
assembly / mainClass := Some("io.github.malyszaryczlowiek.SparkStreamingAnalyser")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

// Added to solve transitive dependency problem with cats
// https://github.com/typelevel/cats/issues/3628
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("shapeless.**" -> "new_shapeless.@1").inAll,
  ShadeRule.rename("cats.kernel.**" -> s"new_cats.kernel.@1").inAll
)

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated