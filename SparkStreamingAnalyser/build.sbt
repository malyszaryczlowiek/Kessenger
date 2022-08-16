ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "SparkStreamingAnalyser",
    idePackagePrefix := Some("com.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",

    externalResolvers += "KessengerLibrary packages" at "https://maven.pkg.github.com/malyszaryczlowiek/KessengerLibrary",

    libraryDependencies ++= Seq(


      // Kafka Repos
//      ("org.apache.kafka" %% "kafka"               % "3.1.0").cross(CrossVersion.for3Use2_13),
//      ("org.apache.kafka" %% "kafka-streams-scala" % "3.1.0").cross(CrossVersion.for3Use2_13),
//      "org.apache.kafka"  % "kafka-clients"        % "3.1.0",



      // Own library with util and domain classes.
      // https://github.com/malyszaryczlowiek/KessengerLibrary
      "com.github.malyszaryczlowiek" %% "KessengerLibrary" % "0.1.19",


      // to switch off logging from slf4j
      //"org.slf4j" % "slf4j-nop" % "1.7.36",


      // For Tests
      "org.scalameta" %% "munit"            % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test


      // for kafka stream tests
      // "org.apache.kafka" % "kafka-streams-test-utils" % "3.1.0" % Test,

    )
  )

// for build JAR executable.
assembly / mainClass := Some("com.github.malyszaryczlowiek.SparkStreamingAnalyser")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated