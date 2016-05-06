import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc
import Dependencies._

object NakadiClient extends Build {

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

def whereToPublishTo(isItSnapshot:Boolean) = {
  val nexus = "https://maven.zalando.net/"
  if (isItSnapshot)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "content/repositories/releases")
  }


val defaultOptions= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code", // Warn when dead code is identified.
  "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
  "-Ywarn-nullary-override", // Warn when non-nullary overrides nullary, e.g. def foo() over def foo.
  "-Ywarn-numeric-widen" // Warn when numerics are widened.
)

lazy val root = project.in(file("."))
  .settings(publishTo := whereToPublishTo(isSnapshot.value))
  .aggregate(api, client)

lazy val api = withDefaults(
    "nakadi-clients-api",
    project.in(file("api"))
    ,true
  ).settings(libraryDependencies ++= apiDeps)

lazy val client = withDefaults(
    "nakadi-clients",
    project.in(file("client")).dependsOn(api)
    ,true
  ).settings(libraryDependencies ++= clientDeps)


  lazy val it = withDefaults(
      "nakadi-integration-test",
      project.in(file("it")).dependsOn(api, client)
    ).settings(libraryDependencies ++= clientDeps)


    lazy val e2e = withDefaults(
        "nakadi-end-2-end-test",
        project.in(file("e2e")).dependsOn(api, client, it)
      ).settings(libraryDependencies ++= clientDeps)


  def withDefaults(projectName:String, project:sbt.Project, publish:Boolean = false)={
    project.settings(
        name := projectName,
        organization := "org.zalando.laas",
        version := "2.0-SNAPSHOT",
        crossPaths := false,
        scalaVersion := "2.11.7",
        publishTo := whereToPublishTo(isSnapshot.value),
        resolvers += Resolver.mavenLocal,
        resolvers += "Maven Central Server" at "http://repo1.maven.org/maven2",
        scalacOptions ++= defaultOptions)
        .configs(Configs.all: _*)
        /*.settings(
          publishArtifact in (Compile, packageDoc) := true //To Publish or Not to Publish scala doc jar
          ,publishArtifact in (Compile, packageSrc) := true //To Publish or Not to Publish src jar
          ,publishArtifact := publish // To Publish or Not to Publish
          ,publishArtifact in Test := false //To Publish or Not to Publish test jar
          ,sources in (Compile,doc) := Seq.empty
          )
          */
  }


}
