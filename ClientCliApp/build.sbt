ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "ClientCliApp",
    idePackagePrefix := Some("com.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",
    libraryDependencies ++= Seq(
      ("org.apache.kafka" %% "kafka" % "3.1.0").cross(CrossVersion.for3Use2_13),
      "org.apache.kafka" % "kafka-clients" % "3.1.0",
      // ("org.apache.kafka" %% "kafka-streams-scala" % "3.1.0").cross(CrossVersion.for3Use2_13)
      ("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4") .cross(CrossVersion.for3Use2_13),
      ("com.github.t3hnar" %% "scala-bcrypt" % "4.3.0").cross(CrossVersion.for3Use2_13), // https://github.com/t3hnar/scala-bcrypt
      "org.postgresql" % "postgresql" % "42.3.3" ,
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,
      // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
      //"org.slf4j" % "slf4j-api" % "1.7.36",

      "org.slf4j" % "slf4j-nop" % "1.7.36",// to switch off logging

      // used in future impelementation
//      "io.circe" %% "circe-core" % "0.14.1",
//      "io.circe" %% "circe-generic" % "0.14.1",
//      "io.circe" %% "circe-parser" % "0.14.1",
    )
  )

assembly / mainClass := Some("com.github.malyszaryczlowiek.MainObject")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated