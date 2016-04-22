import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseCreateSrc
import Dependencies._

object NakadiClient extends Build {

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource

EclipseKeys.withSource := true

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
lazy val commonSettings = Seq(
  organization := "com.example",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

lazy val model = withDefaults(
    "nakadi-klients-model",
    project.in(file("model"))
  ).settings(libraryDependencies ++= modelDeps)

lazy val api = withDefaults(
    "nakadi-klients-api",
    project.in(file("api")).dependsOn(model)
  )

lazy val client = withDefaults(
    "nakadi-klients",
    project.in(file("client")).dependsOn(api, model)
  ).settings(libraryDependencies ++= clientDeps)


  lazy val it = withDefaults(
      "nakadi-integration-test",
      project.in(file("it")).dependsOn(model, api, client)
    ).settings(libraryDependencies ++= clientDeps)


    lazy val e2e = withDefaults(
        "nakadi-end-2-end-test",
        project.in(file("e2e")).dependsOn(model, api, client, it)
      ).settings(libraryDependencies ++= clientDeps)


def withDefaults(projectName:String, project:sbt.Project)={
  project.settings(
      name := projectName,
      organization := "org.zalando.nakadi.client",
      scalaVersion := "2.11.7",
      resolvers += Resolver.mavenLocal,
      resolvers += "Maven Central Server" at "http://repo1.maven.org/maven2",
      scalacOptions ++= defaultOptions)
      .configs(Configs.all: _*)
      .settings(Testing.settings: _*)

}


}
