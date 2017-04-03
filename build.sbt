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
  resolvers += Resolver.bintrayIvyRepo("scalacenter", "sbt-releases"),
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
  .settings(commonSettings, buildSettings, noPublish)
  .aggregate(`sbt-rewrites`)

lazy val `sbt-rewrites` = project
  .settings(publishSettings, buildSettings, commonSettings)
  .settings(
    scalacOptions in Compile := compilerOptions,
    crossScalaVersions := Seq("2.11.8", "2.12.1"),
    // Using 2.11.x until scalafix publishes 2.12 artifacts
    scalaVersion := crossScalaVersions.value.head,
    libraryDependencies ++= Vector(
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      "io.get-coursier" %% "coursier" % "1.0.0-M15",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M15",
      "ch.epfl.scala" %% "scalafix-cli" % "0.3.2",
      "org.scalatest" %% "scalatest" % "3.0.0" % "test"
    )
  )

lazy val `sbt-rewrites-plugin` = project
  .settings(publishSettings, buildSettings, commonSettings)
  .settings(
    sbtPlugin := true,
    bintrayRepository := "releases",
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
