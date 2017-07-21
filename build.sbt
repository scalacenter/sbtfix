// Remember, only aggregates `sbt-rewrites`.
lazy val `sbt-migration-tool` = project
  .in(file("."))
  .settings(noPublish)
  .aggregate(`sbt-rewrites`)
  .settings(watchSources += (baseDirectory in `sbt-rewrites-plugin`).value)

/** Defines the sbt-rewrites project that contains the scalafix code. */
lazy val `sbt-rewrites` = project
  .settings(
    crossScalaVersions := Seq("2.11.11", "2.12.1"),
    // Using 2.11.x until scalafix publishes 2.12 artifacts
    scalaVersion := crossScalaVersions.value.head,
    libraryDependencies ++= Vector(
      "com.github.pathikrit" %% "better-files" % "2.17.1",
      "io.get-coursier" %% "coursier" % "1.0.0-M15-5",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M15-5",
      "ch.epfl.scala" %% "scalafix-cli" % "0.5.0-M1" cross CrossVersion.full,
      "org.scalatest" %% "scalatest" % "3.0.0" % "test"
    ),
    assemblyJarName in assembly :=
      name.value + "_" + scalaVersion.value + "-" + version.value + "-assembly.jar",
    test in assembly := {},
    assemblyMergeStrategy in assembly := {
      case PathList(ps @ _ *) if ps.last endsWith ".class" =>
        MergeStrategy.first
      case PathList(ps @ _ *) if ps.last endsWith ".proto" =>
        MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    publishArtifact in Compile := true,
    packagedArtifact in Compile in packageBin := Def.taskDyn {
      val temp = (packagedArtifact in Compile in packageBin).value
      val toReturn @ (_, slimJar) = temp
      val toEvaluate = replaceJar(slimJar)
      Def.task {
        toEvaluate.value
        toReturn
      }
    }.value,
    Keys.`package` in Compile := Def.taskDyn {
      val slimJar = (Keys.`package` in Compile).value
      val toEvaluate = replaceJar(slimJar)
      Def.task {
        toEvaluate.value
        slimJar
      }
    }.value
  )

/** Defines the sbt-rewrites-plugin which cannot depend on `sbt-rewrites`.
  * `sbt-rewrites` is resolved in sbt 0.13.x explicitly and class-loaded
  * to be able to call Scala 2.12 artifacts from Scala 2.10.x. */
lazy val `sbt-rewrites-plugin` = project
  .settings(scriptedDefaults)
  .settings(
    name := "sbt-migrator",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    bintrayRepository := "sbt-releases",
    publishMavenStyle := false,
    publish := publish.dependsOn(publish in `sbt-rewrites`).value,
    publishLocal :=
      publishLocal.dependsOn(publishLocal in `sbt-rewrites`).value,
    fork in Test := true
  )
