resolvers += Classpaths.sbtPluginReleases
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")
addSbtPlugin("ch.epfl.scala" % "sbt-bintray" % "0.5.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M15-5")
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
