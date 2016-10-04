import sbt._

object Dependencies {

	val akkaVersion = "2.4.7"
	val jacksonVersion = "2.7.3"
	val scalaTestVersion = "2.2.6"
	val junitVersion = "4.12"
	val apiDeps = {
		Seq(
		"com.fasterxml.jackson.module" %% "jackson-module-scala"   						% jacksonVersion,
		"com.fasterxml.jackson.core"   % "jackson-annotations"     						% jacksonVersion,
		"org.scalatest"                %% "scalatest"              						% scalaTestVersion      % "test",
		"junit"                        % "junit"                   						% junitVersion % "test")
	}

	val clientDeps = {
		Seq(
			"com.typesafe"                 % "config"                  						% "1.3.0",
		  "com.google.guava"             % "guava"                   						% "19.0",
		  "com.fasterxml.jackson.core"   % "jackson-core"           						% jacksonVersion,
		  "com.fasterxml.jackson.module" %% "jackson-module-scala"   						% jacksonVersion,
			"com.typesafe.akka"            %% "akka-actor"            	 					% akkaVersion,
			"com.typesafe.akka"            %% "akka-http-experimental" 						% akkaVersion,
			"com.typesafe.akka"            %% "akka-stream" 											% akkaVersion,
			"org.slf4j" 									 % "slf4j-api" 												  % "1.7.21",
			"com.typesafe.akka"            % "akka-slf4j_2.11"                    % "2.4.7",
			"ch.qos.logback"               % "logback-classic"         						% "1.1.7"  % "test",
			"ch.qos.logback"               % "logback-core"         						  % "1.1.7"  % "test",
			"com.typesafe.akka"            %% "akka-testkit"           						% akkaVersion  % "test",
		  "org.scalatest"                %% "scalatest"              						% scalaTestVersion      % "test",
		  "com.google.code.findbugs"     % "jsr305"                  						% "3.0.0"      % "test",
		  "junit"                        % "junit"                   						% junitVersion       % "test",
			"org.mockito" 								 % "mockito-core" 											% "1.10.19"    % "test",
			"com.novocode" 								 % "junit-interface"                    % "0.11"       % "test"
		)
	}

	val itDeps = clientDeps ++ {
		Seq(
			"org.zalando.stups"                 % "tokens"                  						% "0.9.9",
			"org.apache.httpcomponents"         % "httpclient"               						% "4.5.2",
			"org.scalatest"                     %% "scalatest"               						% scalaTestVersion,
			"commons-lang" 											% "commons-lang" 												% "2.6",
			"org.zalando"                       % "jackson-datatype-money"              % "0.6.0",
			"junit"                             % "junit"                   						% junitVersion       % "test",
			"org.slf4j" 									 % "slf4j-api" 												  % "1.7.21",
			"com.typesafe.akka"            % "akka-slf4j_2.11"                    % "2.4.7",
			"ch.qos.logback"               % "logback-classic"         						% "1.1.7"  % "test",
			"ch.qos.logback"               % "logback-core"         						  % "1.1.7"  % "test"
		)
	}
}
