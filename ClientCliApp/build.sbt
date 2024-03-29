ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "ClientCliApp",
    idePackagePrefix := Some("io.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",

    //externalResolvers += "KessengerLibrary packages" at "https://maven.pkg.github.com/malyszaryczlowiek/KessengerLibrary",

    libraryDependencies ++= Seq(

      // Kafka Repos
      ("org.apache.kafka" %% "kafka"         % "3.1.0").cross(CrossVersion.for3Use2_13),
      "org.apache.kafka"  %  "kafka-clients" % "3.1.0",


      // For usage of Scala's parallel collections
      ("org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.4").cross(CrossVersion.for3Use2_13),


      // For hashing password with BCrypt algorithm    // https://github.com/t3hnar/scala-bcrypt
      ("com.github.t3hnar" %% "scala-bcrypt" % "4.3.0").cross(CrossVersion.for3Use2_13),


      // for connecting to PostgreSQL db
      "org.postgresql" % "postgresql" % "42.3.3",


      // logging api
      "org.apache.logging.log4j" % "log4j-api"  % "2.18.0",
      "org.apache.logging.log4j" % "log4j-core" % "2.18.0",


      // to switch off logging warning from slf4j
      "org.slf4j" % "slf4j-nop" % "1.7.36",


      // Own library with util and domain classes.
      // https://github.com/malyszaryczlowiek/kessenger-lib
      "io.github.malyszaryczlowiek" %% "kessenger-lib" % "0.2.1",


      // For Tests
      "org.scalameta" %% "munit"            % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test
    )
  )


// for build JAR executable.
assembly / mainClass := Some("io.github.malyszaryczlowiek.MainObject")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated