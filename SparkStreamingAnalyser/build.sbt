ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "2.12.16"

lazy val root = (project in file("."))
  .settings(
    name := "SparkStreamingAnalyser",
    idePackagePrefix := Some("com.github.malyszaryczlowiek"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar",

    libraryDependencies ++= Seq(

      "org.apache.spark" %% "spark-sql"            % "3.3.0" % "provided",
      "org.apache.spark" %% "spark-sql-kafka-0-10" % "3.3.0" ,//% "provided",

      "io.circe" %% "circe-core"    % "0.14.2",
      "io.circe" %% "circe-generic" % "0.14.2",
      "io.circe" %% "circe-parser"  % "0.14.2",

      // to solve transitive dependency error with spark and circle
      "org.scalanlp" %% "breeze" % "2.1.0"
    )
  )

// for build JAR executable.
assembly / mainClass := Some("com.github.malyszaryczlowiek.SparkStreamingAnalyser")
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

// Added to solve this transitive dependency problem with cats
// https://github.com/typelevel/cats/issues/3628
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("shapeless.**" -> "new_shapeless.@1").inAll,
  ShadeRule.rename("cats.kernel.**" -> s"new_cats.kernel.@1").inAll
)

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated