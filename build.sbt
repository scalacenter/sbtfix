lazy val `sbt-rewrites` = project.in(file("."))

name := "sbt-rewrites"

organization := "me.vican.jorge"

// Using 2.11.x until scalafix publishes 2.12 artifacts
scalaVersion := crossScalaVersions.value.head

crossScalaVersions := Seq("2.11.8", "2.12.1")

resolvers += Resolver.bintrayRepo("jvican", "releases")

libraryDependencies ++= Vector(
  "com.github.pathikrit" %% "better-files" % "2.17.1",
  "io.get-coursier" %% "coursier" % "1.0.0-M15",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M15",
  "ch.epfl.scala" %% "scalafix-cli" % "0.3.2",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

licenses := Seq("MPL-2.0" -> url("https://opensource.org/licenses/MPL-2.0"))
pomExtra in Global := {
  <developers>
    <developer>
      <id>jvican</id>
      <name>Jorge Vicente Cantero</name>
      <url>https://github.com/jvican</url>
    </developer>
  </developers>
}
