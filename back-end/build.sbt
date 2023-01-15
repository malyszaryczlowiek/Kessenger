name := """back-end"""
organization := "io.github.malyszaryczlowiek"
version := "1.0-SNAPSHOT"
lazy val root = (project in file(".")).enablePlugins(PlayScala)
scalaVersion := "2.13.10"
libraryDependencies ++= Seq(
  guice,
  jdbc,
  // Own library with util and domain classes.
  // https://github.com/malyszaryczlowiek/kessenger-lib
  "io.github.malyszaryczlowiek" %% "kessenger-lib" % "0.3.19",

  "org.apache.kafka" %% "kafka"               % "3.1.0",


  // for connecting to PostgreSQL db
  "org.postgresql" % "postgresql" % "42.3.3",


  // For hashing password with BCrypt algorithm
  // https://github.com/t3hnar/scala-bcrypt
  "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0",


  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "io.github.malyszaryczlowiek.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "io.github.malyszaryczlowiek.binders._"
