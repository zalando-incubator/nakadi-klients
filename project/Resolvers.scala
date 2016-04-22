import sbt._
import Keys._

object Resolvers {

  val settings = Seq(
    resolvers += Resolver.mavenLocal,
    resolvers += "Maven Central Server" at "http://repo1.maven.org/maven2"
  )
}
