package sbt.rewrite.plugin

import java.net.URLClassLoader
import java.lang.reflect

import sbt._
import Keys._
import sbt.plugins.JvmPlugin

trait SbtMigrationKeys {
  val migrateSbtBuild = taskKey[Unit]("Migrate 0.13 builds to 1.0 builds.")
  val sbtMigrationJar = taskKey[File]("Location of the sbt migration tool.")
}

object SbtMigrationPlugin$
  extends AutoPlugin
    with SbtMigrationKeys
    with SbtUtils
    with ReflectionUtils {

  object autoImport extends SbtMigrationKeys

  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = JvmPlugin

  val zincStub211 = stubProjectFor("2.11.8", SbtMigrationVersion)
  val zincStub212 = stubProjectFor("2.12.1", SbtMigrationVersion)

  override def extraProjects: Seq[Project] = Seq(zincStub211, zincStub212)

  private lazy val rootLoader = {
    def parent(loader: ClassLoader): ClassLoader = {
      val p = loader.getParent
      if (p eq null) loader else parent(p)
    }
    val systemLoader = ClassLoader.getSystemClassLoader
    if (systemLoader ne null) parent(systemLoader)
    else parent(getClass.getClassLoader)
  }

  override def projectSettings: Seq[Def.Setting[_]] = {
    Seq(
      sbtMigrationJar := getZincMegaJar(zincStub211, zincStub212).value,
      migrateSbtBuild := {
        val st = streams.value
        val logger = st.log
        logger.info("Migrating your sbt 0.13 build file to 1.0.")
        // Empty for now
        ???
      }
    )
  }
}

trait ReflectionUtils {
  protected val ZincProxyFQN = "sbt.zinc.ZincProxy"
  protected val RunMethod = "run"
  protected val CompilerBridgeMethod = "compileBridge"

  def getProxyMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(
      RunMethod,
      classOf[String],
      classOf[File],
      classOf[File],
      classOf[Array[File]],
      classOf[File],
      classOf[File],
      classOf[Array[File]],
      classOf[Array[File]],
      classOf[File],
      classOf[Array[String]],
      classOf[Array[String]],
      classOf[String],
      classOf[scala.Boolean]
    )
  }

  def getCompileMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(CompilerBridgeMethod, classOf[Object])
  }
}

/** Define sbt utils for the plugin. */
trait SbtUtils { self: SbtMigrationKeys =>
  protected val Version = "2\\.(\\d\\d)\\..*".r
  protected val SbtMigrationName = "sbt-rewrites"
  protected val SbtMigrationGroupId = "jorge.vican.me"
  protected val SbtMigrationVersion = "0.1.0"

  private def failedResolution(report: UpdateReport) =
    s"Unable to resolve the Zinc proxy: $report"

  protected def getJar(report: UpdateReport, zincVersion: String): File = {
    val jarVersion = s".*${SbtMigrationName}_2.1[12](-$zincVersion)?.jar$$"
    val jar = report.allFiles.find(f => f.getAbsolutePath.matches(jarVersion))
    jar.getOrElse(throw new IllegalStateException(failedResolution(report)))
  }

  protected def stubProjectFor(version: String, zincVersion: String): Project = {
    val Version(id) = version
    Project(id = s"sbt-rewrites-$id", base = file(s"project/sbt-rewrites/$id"))
      .settings(
        description := "Rewrite sbt builds from 0.13 to 1.0.",
        publishLocal := {},
        publish := {},
        publishArtifact := false,
        scalaVersion := version,
        // remove injected dependencies from random sbt plugins.
        libraryDependencies := Nil,
        libraryDependencies +=
          (SbtMigrationGroupId %% SbtMigrationName % zincVersion),
        resolvers += Resolver.bintrayRepo("scalacenter", "releases")
      )
  }

  protected def getZincMegaJar(stubFor211: Project, stubFor212: Project) = {
    Def.taskDyn[File] {
      val updateAction = {
        Keys.scalaVersion.value match {
          case Version("11") => Keys.update in stubFor211
          case Version("12") => Keys.update in stubFor212
          case _ => sys.error("Only Scala 2.11 or 2.12 are supported.")
        }
      }
      Def.task(getJar(updateAction.value, SbtMigrationVersion))
    }
  }
}
