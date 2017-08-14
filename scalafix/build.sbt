val Scalafix = _root_.scalafix.Versions
val sbthostVersion = "0.3.0"
inThisBuild(
  List(
    scalaVersion := Scalafix.scala212,
    organization := "ch.epfl.scala"
  )
)

lazy val root = project.in(file(".")).aggregate(rewrites, input, output, tests)

lazy val rewrites = project.settings(
  libraryDependencies ++= Seq(
    "org.scalameta" %% "sbthost-runtime" % sbthostVersion,
    "ch.epfl.scala" %% "scalafix-core" % Scalafix.version
  )
)

lazy val input = project.settings(
  scalacOptions += "-Xplugin-require:sbthost",
  scalaVersion := "2.10.6",
  sbtPlugin := true,
  scalacOptions += s"-P:sbthost:sourceroot:${sourceDirectory.in(Compile).value}",
  addCompilerPlugin(
    "org.scalameta" % "sbthost-nsc" % sbthostVersion cross CrossVersion.full)
)

lazy val output = project.settings(
  scalaVersion := "2.10.6",
  sbtPlugin := true
)

lazy val tests = project
  .settings(
    libraryDependencies ++= Seq(
      "org.scalameta" %% "scalameta" % Scalafix.scalameta,
      "ch.epfl.scala" % "scalafix-testkit" % Scalafix.version % Test cross CrossVersion.full
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
