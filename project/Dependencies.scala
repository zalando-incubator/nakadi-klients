import sbt._

object Dependencies {

	val akkaVersion = "2.4.2"

	val modelDeps = {Seq(
	"com.fasterxml.jackson.module" %% "jackson-module-scala"   						% "2.6.3"     withSources() withJavadoc(),
	"com.fasterxml.jackson.core"   % "jackson-core"           						% "2.7.0"     withSources() withJavadoc())}
	val clientDeps = {
		Seq(
			"com.typesafe.akka" 					 %% "akka-http-spray-json-experimental" % akkaVersion withSources() withJavadoc(),
		  "com.typesafe.akka"            %% "akka-actor"            	 					% akkaVersion withSources() withJavadoc(),
			"com.typesafe.akka"            %% "akka-http-experimental" 						% akkaVersion withSources() withJavadoc(),
			"com.typesafe.akka"            %% "akka-stream" 											% akkaVersion withSources() withJavadoc(),
	    "com.typesafe.akka"         	 %% "akka-cluster-metrics"   						% akkaVersion withSources() withJavadoc(),
		  "com.typesafe.akka"            %% "akka-testkit"           						% akkaVersion % "test" withSources() withJavadoc(),
			"joda-time" 									 %  "joda-time" 												% "2.9.3" withSources() withJavadoc(),
			"org.joda" 										 % "joda-convert" 											% "1.8.1" withSources() withJavadoc(),
			"com.typesafe"                 % "config"                  						% "1.3.0"     withSources() withJavadoc(),
		  "com.google.guava"             % "guava"                   						% "19.0"      withSources() withJavadoc(),
		  "com.typesafe.scala-logging"   %% "scala-logging"          						% "3.1.0"     withSources() withJavadoc(),
			"io.spray" 										 %% "spray-json" 												% "1.3.2"     withSources() withJavadoc(),
		  "com.fasterxml.jackson.core"   % "jackson-core"           						% "2.7.0"     withSources() withJavadoc(),
		  "com.fasterxml.jackson.module" %% "jackson-module-scala"   						% "2.6.3"     withSources() withJavadoc(),
		  "ch.qos.logback"               % "logback-classic"         						% "1.1.3"     withSources() withJavadoc(),
		  "org.scalatest"                %% "scalatest"              						% "2.2.6" % "test" withSources() withJavadoc(),
		  //"io.undertow"                  % "undertow-core"           					% "1.2.12.Final" % "test" withSources() withJavadoc(),
		  //"io.undertow"                  % "undertow-servlet"        					% "1.2.12.Final" % "test" withSources() withJavadoc(),
		  "org.apache.commons"           % "commons-io"              						% "1.3.2" % "test" withSources() withJavadoc(),
		  //"com.google.code.findbugs"     % "jsr305"                  					% "1.3.9" % "test" withSources() withJavadoc(),
		  "junit"                        % "junit"                   						% "4.12" % "test" withSources() withJavadoc(),
			"org.mockito" 								 % "mockito-core" 											% "1.10.19" % "test" withSources() withJavadoc()
		)
	}
}
