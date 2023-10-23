ThisBuild / version := "0.1.0"
// ThisBuild / scalaVersion := "3.1.1"
ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "kafka-streams-chat-analyser",
    idePackagePrefix := Some("io.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",


    libraryDependencies ++= Seq(

      // Kafka Repos
//      ("org.apache.kafka" %% "kafka"               % "3.1.0").cross(CrossVersion.for3Use2_13),
//      ("org.apache.kafka" %% "kafka-streams-scala" % "3.1.0").cross(CrossVersion.for3Use2_13),
      "org.apache.kafka" %% "kafka" % "3.1.0",
      "org.apache.kafka" %% "kafka-streams-scala" % "3.1.0",
      "org.apache.kafka"  % "kafka-clients"       % "3.1.0",

      // Own library with util and domain classes.
      // https://github.com/malyszaryczlowiek/kessenger-lib
      "io.github.malyszaryczlowiek" %% "kessenger-lib" % "0.3.26",

      // https://github.com/lightbend/config
      "com.typesafe" % "config" % "1.4.2",


      // logging
      "com.typesafe.play" %% "play-logback" % "2.8.19",
//      "ch.qos.logback" % "logback-core"    % "1.4.8",
//      "ch.qos.logback" % "logback-classic" % "1.4.8",
//      "org.slf4j"      % "slf4j-api"       % "2.0.7",

      // to switch off logging from slf4j
      // "org.slf4j" % "slf4j-nop" % "2.0.7",


      //      "org.apache.logging.log4j" % "log4j-api" % "2.20.0",
//      "org.apache.logging.log4j" % "log4j-core" % "2.20.0",
      // "org.slf4j" % "slf4j-nop" % "2.0.5",
      // "org.slf4j" % "slf4j-api" % "2.0.5",
      // "ch.qos.logback" % "logback-core" % "1.4.6",


      // For Tests
      "org.scalameta" %% "munit"            % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test,


      // for kafka stream tests
      "org.apache.kafka" % "kafka-streams-test-utils" % "3.1.0" % Test,

    )
  )

// for build JAR executable.
assembly / mainClass := Some("io.github.malyszaryczlowiek.StreamsChatAnalyser")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated

