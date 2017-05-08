import sbt.Keys._

lazy val projectSettings = Seq(
  organization := "com.my",
  scalaVersion := "2.12.1",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")
)

val akkaVersion = "2.4.16"
val akkaHttpVersion = "10.0.3"

lazy val root = Project(id = "file-processor", base = file("."))
  .settings(projectSettings: _*)
  .settings(
    name := "FileProcessing",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.4",
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "org.scalatest" %% "scalatest" % "3.0.1" % Test
    )
  )