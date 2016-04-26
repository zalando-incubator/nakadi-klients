
name := "nakadi-klients"

organization := "org.zalando.nakadi.client"

scalaVersion := "2.11.6"

lazy val root = (project in file(".")).enablePlugins(GitVersioning)

crossPaths := false

scalacOptions ++= Seq(
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

resolvers += Resolver.mavenLocal
resolvers += "Maven Central Server" at "http://repo1.maven.org/maven2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.google.guava" % "guava" % "19.0",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.6.3",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2",
  "com.typesafe" % "config" % "1.3.0",    // good to mention to fix the version (though Akka brings it in as a transitive dependency)

  "com.typesafe.akka" %% "akka-testkit" % "2.3.11" % "test",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test",
  "io.undertow" % "undertow-core"    % "1.2.12.Final" % "test",
  "io.undertow" % "undertow-servlet" % "1.2.12.Final" % "test",
  "org.apache.commons" % "commons-io" % "1.3.2" % "test",
  "com.google.code.findbugs" % "jsr305" % "1.3.9" % "test",
  "junit" % "junit" % "4.12" % "test"
)

git.baseVersion := "0.0.0"

version := "1.0"

publishMavenStyle := true

publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
