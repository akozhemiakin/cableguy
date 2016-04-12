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

lazy val noPublish = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomIncludeRepository := { _ => false },
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/akozhemiakin/cableguy")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/akozhemiakin/cableguy"),
      "scm:git:git@github.com:akozhemiakin/cableguy.git"
    )
  ),
  pomExtra := (
    <developers>
      <developer>
        <id>akozhemiakin</id>
        <name>Artyom Kozhemiakin</name>
        <url>http://arkoit.ru</url>
      </developer>
    </developers>)
)

lazy val allSettings = commonSettings ++ publishSettings

lazy val root = (project in file("."))
  .settings(allSettings)
  .settings(noPublish)
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
