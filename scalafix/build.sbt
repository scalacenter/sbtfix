// Use a scala version supported by scalafix.
scalaVersion in ThisBuild := org.scalameta.BuildInfo.supportedScalaVersions.last
lazy val root = project.in(file(".")).aggregate(rewrites, input, output, tests)

lazy val sharedSettings = Def.settings(
  organization := "ch.epfl.scala",
  resolvers += Resolver.bintrayRepo("scalameta", "maven")
)

lazy val rewrites = project.settings(
  sharedSettings,
  libraryDependencies ++= Seq(
    "org.scalameta" %% "sbthost-runtime" % "0.1.0-RC3",
    "ch.epfl.scala" %% "scalafix-core" % "0.4.2"
  )
)

lazy val input = project.settings(
  sharedSettings,
  scalacOptions += "-Xplugin-require:sbthost",
  scalaVersion := "2.10.6",
  sbtPlugin := true,
  scalacOptions += s"-P:sbthost:sourceroot:${sourceDirectory.in(Compile).value}",
  addCompilerPlugin(
    "org.scalameta" % "sbthost-nsc" % "0.1.0-RC3" cross CrossVersion.full)
)

lazy val output = project.settings(
  scalaVersion := "2.10.6",
  sbtPlugin := true
)

lazy val tests = project
  .settings(
    sharedSettings,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % org.scalameta.BuildInfo.version,
      "ch.epfl.scala" % "scalafix-testkit" % "0.4.2" % Test cross CrossVersion.full
    ),
    test.in(Test) := test.in(Test).dependsOn(compile.in(input, Compile)).value,
    buildInfoPackage := "sbtfix",
    buildInfoKeys := Seq[BuildInfoKey](
      "inputSourceRoot" ->
        sourceDirectory.in(input, Compile).value,
      "outputSourceRoot" ->
        sourceDirectory.in(output, Compile).value,
      "inputClassDirectory" ->
        classDirectory.in(input, Compile).value
    )
  )
  .dependsOn(rewrites)
  .enablePlugins(BuildInfoPlugin)
