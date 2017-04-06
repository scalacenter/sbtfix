lazy val publishSettings: Seq[Setting[_]] = Seq(
  publishMavenStyle := true,
  bintrayOrganization := Some("scalacenter"),
  bintrayRepository := "releases",
  bintrayPackageLabels := Seq("sbt", "scalafix", "migration"),
  publishTo := (publishTo in bintray).value,
  publishArtifact in Test := false,
  // This repository is under MPLv2 because it was originally @jvican's
  // personal project and then was moved to Scala Center governance.
  licenses := Seq("MPL-2.0" -> url("https://opensource.org/licenses/MPL-2.0")),
  homepage := Some(url("https://github.com/scalaplatform/platform")),
  autoAPIMappings := true,
  apiURL := Some(url("https://scalaplatform.github.io/platform")),
  pomExtra :=
    <developers>
      <developer>
        <id>jvican</id>
        <name>Jorge Vicente Cantero</name>
        <url></url>
      </developer>
    </developers>
)

lazy val noPublish = Seq(
  publishArtifact := false,
  publish := {},
  PgpKeys.publishSigned := {},
  publishLocal := {}
)

lazy val buildSettings: Seq[Setting[_]] = Seq(
  organization := "ch.epfl.scala",
  resolvers += Resolver.jcenterRepo,
  resolvers += Resolver.bintrayRepo("scalameta", "maven"),
  updateOptions := updateOptions.value.withCachedResolution(true)
)

lazy val compilerOptions = Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture",
  "-Xlint"
)

lazy val commonSettings: Seq[Setting[_]] = Seq(
  triggeredMessage in ThisBuild := Watched.clearWhenTriggered,
  watchSources += baseDirectory.value / "resources",
  scalacOptions in (Compile, console) := compilerOptions,
  testOptions in Test += Tests.Argument("-oD")
)

// Don't aggregate the plugin here
lazy val `sbt-migration-tool` = project
  .in(file("."))
  .settings(commonSettings, buildSettings, noPublish)
  .aggregate(`sbt-rewrites`)
  .settings(watchSources += (baseDirectory in `sbt-rewrites-plugin`).value)

lazy val `sbt-rewrites` = project
  .settings(publishSettings, buildSettings, commonSettings)
  .settings(
    scalacOptions in Compile := compilerOptions,
    crossScalaVersions := Seq("2.11.9", "2.12.1"),
    // Using 2.11.x until scalafix publishes 2.12 artifacts
    scalaVersion := crossScalaVersions.value.head,
    libraryDependencies ++= Vector(
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      "io.get-coursier" %% "coursier" % "1.0.0-M15-5",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M15-5",
      "ch.epfl.scala" % "scalafix-cli" % "0.3.3+18-3f58c785" cross CrossVersion.full,
      "org.scalatest" %% "scalatest" % "3.0.0" % "test"
    ),
    assemblyJarName in assembly :=
      name.value + "_" + scalaVersion.value + "-" + version.value + "-assembly.jar",
    test in assembly := {},
    packagedArtifact in Compile in packageBin := {
      val temp = (packagedArtifact in Compile in packageBin).value
      val (art, slimJar) = temp
      val fatJar =
        new File(crossTarget.value + "/" + (assemblyJarName in assembly).value)
      val _ = assembly.value
      IO.copy(List(fatJar -> slimJar), overwrite = true)
      (art, slimJar)
    },
    publishArtifact in Compile := true,
    Keys.`package` in Compile := {
      val slimJar = (Keys.`package` in Compile).value
      val fatJar =
        new File(crossTarget.value + "/" + (assemblyJarName in assembly).value)
      val _ = assembly.value
      IO.copy(List(fatJar -> slimJar), overwrite = true)
      slimJar
    }
  )

lazy val `sbt-rewrites-plugin` = project
  .settings(publishSettings, buildSettings, commonSettings)
  .settings(ScriptedPlugin.scriptedSettings: Seq[Setting[_]])
  .settings(
    name := "sbt-migrator",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    bintrayRepository := "sbt-releases",
    publishMavenStyle := false,
    publishLocal := {
      publishLocal
        .dependsOn(publishLocal in `sbt-rewrites`)
        .value
    },
    publish := {
      publish
        .dependsOn(publishLocal in `sbt-rewrites`)
        .value
    },
    scriptedLaunchOpts := Seq(
      "-Dplugin.version=" + version.value,
      "-Xmx1g",
      "-Xss16m"
    ) ++ {
      // Pass along custom boot properties if specified
      val bootProps = "sbt.boot.properties"
      sys.props.get(bootProps).map(x => s"-D$bootProps=$x").toList
    },
    scriptedBufferLog := false,
    fork in Test := true
  )
