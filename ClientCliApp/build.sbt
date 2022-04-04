ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.1.1"

lazy val root = (project in file("."))
  .settings(
    name := "ClientCliApp",
    idePackagePrefix := Some("com.github.malyszaryczlowiek")
    // assembly / assemblyJarName := name.+"-"+version+".jar"
    // TODO check how to change jar name
    // assemblyOutputPath in assembly := "..."
  )

assembly / mainClass := Some("com.github.malyszaryczlowiek.MainObject")

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

Compile / run := Defaults.runTask(Compile / fullClasspath, Compile / run / mainClass, Compile / run / runner).evaluated