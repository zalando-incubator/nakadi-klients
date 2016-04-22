resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")

addSbtPlugin("org.scoverage" 	% "sbt-scoverage" % "1.0.4")

addSbtPlugin("org.scoverage" 	% "sbt-coveralls" % "1.0.0")

addSbtPlugin("com.codacy" 		% "sbt-codacy-coverage" % "1.1.0")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0")
