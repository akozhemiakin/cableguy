lazy val versions = new {
  val scala = "2.11.8"
  val shapeless = "2.3.0"
}

lazy val commonSettings = Seq(
  version := "0.1.0-SNAPSHOT",
  organization := "ru.arkoit",
  scalaVersion := versions.scala,
  autoAPIMappings := true,
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  ),
  scalacOptions ++= Seq("-feature", "-language:implicitConversions")
)

lazy val allSettings = commonSettings

lazy val root = (project in file("."))
  .settings(allSettings)
  .aggregate(core)

lazy val core = project
  .settings(allSettings)
  .settings(Seq(
    moduleName := "cableguy-core",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.chuusai" %% "shapeless" % versions.shapeless
    )
  ))
