ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "ClientCliApp",
    idePackagePrefix := Some("com.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",
    libraryDependencies ++= Seq(
      ("org.apache.kafka" %% "kafka" % "3.1.0").cross(CrossVersion.for3Use2_13),
      "org.apache.kafka" % "kafka-clients" % "3.1.0"

// ("org.apache.kafka" %% "kafka-streams-scala" % "3.1.0").cross(CrossVersion.for3Use2_13)
    )
  )

assembly / mainClass := Some("com.github.malyszaryczlowiek.MainObject")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated