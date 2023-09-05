ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    name := "spark-graphx-analyser",
    idePackagePrefix := Some("io.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",


    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-sql"    % "3.3.0" % "provided",
      "org.apache.spark" %% "spark-graphx" % "3.3.0",

      // slf4j
      "org.slf4j" % "slf4j-nop" % "2.0.5",

      //db
      "org.postgresql" % "postgresql" % "42.3.3",
      // org.postgresql:postgresql:42.3.3

      // log4j logger
      "org.apache.logging.log4j" % "log4j-api"  % "2.20.0",
      "org.apache.logging.log4j" % "log4j-core" % "2.20.0",


      // https://github.com/lightbend/config
      "com.typesafe" % "config" % "1.4.2",

      // for tests
      "org.scalameta" %% "munit"            % "0.7.29" % Test,
      "org.scalameta" %% "munit-scalacheck" % "0.7.29" % Test


    )

  )

// for build JAR executable.
assembly / mainClass := Some("io.github.malyszaryczlowiek.SparkGraphX")
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
