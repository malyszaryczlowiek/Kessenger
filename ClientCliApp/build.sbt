ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "ClientCliApp",
    idePackagePrefix := Some("com.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",
    libraryDependencies ++= Seq(
      ("org.apache.kafka" %% "kafka" % "3.1.0").cross(CrossVersion.for3Use2_13),
      // ("org.apache.kafka" %% "kafka-streams-scala" % "3.1.0").cross(CrossVersion.for3Use2_13)
      "org.apache.kafka" % "kafka-clients" % "3.1.0",
      "io.circe" %% "circe-core" % "0.14.1",
      "io.circe" %% "circe-generic" % "0.14.1",
      "io.circe" %% "circe-parser" % "0.14.1",
      "org.postgresql" % "postgresql" % "42.3.3" ,
      ("com.github.t3hnar" %% "scala-bcrypt" % "4.3.0").cross(CrossVersion.for3Use2_13),// for bcrypt
      // https://mvnrepository.com/artifact/com.github.t3hnar/scala-bcrypt
      // https://github.com/t3hnar/scala-bcrypt
      "org.scalameta" %% "munit" % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test
    )
  )

assembly / mainClass := Some("com.github.malyszaryczlowiek.MainObject")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated