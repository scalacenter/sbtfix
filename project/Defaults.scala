import sbt._
import bintray.BintrayKeys
import com.typesafe.sbt.pgp.PgpKeys

object Defaults extends AutoPlugin {
  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = plugins.JvmPlugin

  import DefaultValues._
  object autoImport extends AutoImported

  override def globalSettings: Seq[Setting[_]] = List(
    // The sbt-pgp requirements for tag-driven releases and Maven Central sync
    PgpKeys.pgpPassphrase := sys.env.get("PGP_PASSWORD").map(_.toArray),
    PgpKeys.pgpPublicRing := file("LOCATION_OF_YOUR_CI_PUBLIC_RING"),
    PgpKeys.pgpSecretRing := file("LOCATION_OF_YOUR_CI_SECRET_RING"),
    BintrayKeys.bintrayOrganization := Some(Org),
    BintrayKeys.bintrayRepository := "releases",
    Keys.licenses := Seq(
      "MPL-2.0" -> url("https://opensource.org/licenses/MPL-2.0")),
    Keys.homepage := Some(url(s"https://github.com/$ProjectHandle")),
    Keys.scmInfo := Some(
      ScmInfo(url(s"https://github.com/$ProjectHandle"),
              s"scm:git:git@github.com:$ProjectHandle.git")),
    Keys.developers := List(
      Developer("jvican",
                "Jorge Vicente Cantero",
                "jorge@vican.me",
                url("https://github.com/jvican"))
    )
  )

  override def buildSettings: Seq[Setting[_]] = List(
    Keys.organization := "ch.epfl.scala",
    Keys.resolvers += Resolver.jcenterRepo,
    Keys.resolvers += Resolver.bintrayRepo("scalameta", "maven"),
    Keys.updateOptions := Keys.updateOptions.value.withCachedResolution(true)
  )

  override def projectSettings: Seq[Setting[_]] = List(
    Keys.publishMavenStyle := true,
    BintrayKeys.bintrayPackageLabels := Seq("sbt", "scalafix", "migration"),
    Keys.publishArtifact in Test := false,
    Keys.triggeredMessage in ThisBuild := Watched.clearWhenTriggered,
    Keys.watchSources += Keys.baseDirectory.value / "resources",
    Keys.testOptions in Test += Tests.Argument("-oD"),
    Keys.scalacOptions in (Compile, Keys.console) := compilerOptions,
    Keys.publishArtifact in Keys.packageDoc := false
  )
}

trait AutoImported {
  final val noPublish: Seq[Setting[_]] = List(
    Keys.publishArtifact := false,
    Keys.publish := {},
    PgpKeys.publishSigned := {},
    Keys.publishLocal := {}
  )

  final val scriptedDefaults = ScriptedPlugin.scriptedSettings ++ Seq(
    ScriptedPlugin.scriptedLaunchOpts :=
      Seq(s"-Dplugin.version=${Keys.version.value}", "-Xmx1g", "-Xss16m"),
    ScriptedPlugin.scriptedBufferLog := false
  )

  def replaceJar(slimJar: File): Def.Initialize[Task[Unit]] = Def.task {
    import sbtassembly.AssemblyKeys
    val fileName = (AssemblyKeys.assemblyJarName in AssemblyKeys.assembly).value
    val fatJar = new File(s"${Keys.crossTarget.value}/$fileName")
    val _ = AssemblyKeys.assembly.value
    IO.copy(List(fatJar -> slimJar), overwrite = true)
  }
}

object DefaultValues {
  final val Org = "scalacenter"
  final val ProjectHandle = s"$Org/sbtfix"
  final val compilerOptions: Seq[String] = List(
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
}
