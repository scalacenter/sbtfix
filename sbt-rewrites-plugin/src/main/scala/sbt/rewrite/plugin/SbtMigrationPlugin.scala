package sbt.rewrite.plugin

import java.net.URLClassLoader
import java.lang.reflect
import java.io.File

import sbt.{Def, _}
import sbt.Keys._
import sbt.plugins.JvmPlugin

import scala.util.matching.Regex

trait SbtMigrationKeys {
  val migrateSbtBuild = taskKey[Unit]("Migrate 0.13 builds to 1.0 builds.")
  val sbtMigrationJar = taskKey[File]("Location of the sbt migration tool.")
}

object SbtMigrationPlugin
    extends AutoPlugin
    with PluginProcessor
    with SbtMigrationKeys
    with SbtBootstrapUtil
    with ReflectionUtils {

  object autoImport extends SbtMigrationKeys

  override def trigger: PluginTrigger = AllRequirements
  override def requires: Plugins = JvmPlugin

  val sbtMigration211: Project = stubProjectFor("2.11.8", SbtMigrationVersion)
  val sbtMigration212: Project = stubProjectFor("2.12.1", SbtMigrationVersion)

  override def extraProjects: Seq[Project] =
    Seq(sbtMigration211, sbtMigration212)

  private lazy val rootLoader = {
    def parent(loader: ClassLoader): ClassLoader = {
      val p = loader.getParent
      if (p eq null) loader else parent(p)
    }
    val systemLoader = ClassLoader.getSystemClassLoader
    if (systemLoader ne null) parent(systemLoader)
    else parent(getClass.getClassLoader)
  }

  private var cachedInstance: Any = _
  private var cachedEntrypoint: reflect.Method = _

  override def globalSettings: Seq[Def.Setting[_]] = {
    Seq(
      sbtMigrationJar := getZincMegaJar(sbtMigration211, sbtMigration212).value,
      migrateSbtBuild := {
        val st = streams.value
        val logger = st.log
        logger.info("Migrating your sbt 0.13 build file to 1.0...")
        if (cachedInstance == null) {
          // The migration jar is beefed up with all its dependencies
          val migrationJar = sbtMigrationJar.value
          val jarsToClassload = Path.toURLs(List(migrationJar))
          val cachedLoader = new URLClassLoader(jarsToClassload, rootLoader)
          val clazz = Class.forName(SbtMigrationProxy, false, cachedLoader)
          cachedInstance = clazz.newInstance()
          cachedEntrypoint = getProxyMethod(clazz)
        }
        val settingInfos = allSettingInfos(state.value)
        val sbtDir: File = (baseDirectory in ThisBuild).value
        cachedEntrypoint.invoke(cachedInstance, sbtDir, settingInfos)
      }
    )
  }
}

trait PluginProcessor {
  def allSettingInfos(state: State): Array[Array[String]] = {
    val logger = state.log
    val extracted = Project.extract(state)
    val autoPlugins = extracted.currentProject.autoPlugins
    def getType(setting: Setting[_]): String =
      setting.key.key.manifest.toString
    val settingsWithTypes = autoPlugins.flatMap { plugin =>
      logger.info(s"Analyzing keys of ${plugin.label}.")
      val settings = plugin.projectSettings.++(
        plugin.buildSettings.++(plugin.globalSettings))
      settings.map(s => s.key.key.label -> getType(s))
    }.distinct
    // Converting to arrays to pass them as arguments via reflection
    settingsWithTypes.map(t => Array(t._1, t._2)).toArray
  }
}

trait ReflectionUtils {
  protected val SbtMigrationProxy = "sbt.rewrite.MigrationTool"
  protected val RunMethod = "migrateBuildFromSbt"

  def getProxyMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(
      RunMethod,
      classOf[File],
      classOf[Array[Array[String]]]
    )
  }
}

/** Define sbt utils for the plugin. */
trait SbtBootstrapUtil { self: SbtMigrationKeys =>
  protected val Version: Regex = "2\\.(\\d\\d)\\..*".r
  protected val SbtMigrationName = "sbt-rewrites"
  protected val SbtMigrationGroupId = "ch.epfl.scala"
  protected val SbtMigrationVersion = "0.1.0-SNAPSHOT"

  private def failedResolution(report: UpdateReport) =
    s"Unable to resolve the Zinc proxy: $report"

  private def getJar(report: UpdateReport, migratorVersion: String): File = {
    val jarVersion = s".*${SbtMigrationName}_2.1[12](-$migratorVersion)?.jar$$"
    val jar = report.allFiles.find(f => f.getAbsolutePath.matches(jarVersion))
    jar.getOrElse(throw new IllegalStateException(failedResolution(report)))
  }

  protected def stubProjectFor(version: String,
                               migratorVersion: String): Project = {
    val Version(id) = version
    Project(id = s"sbt-migrator-$id", base = file(s"project/sbt-migrator/$id"))
      .settings(
        description := "Rewrite sbt builds from 0.13 to 1.0.",
        publishLocal := {},
        publish := {},
        publishArtifact := false,
        scalaVersion := version,
        // remove injected dependencies from random sbt plugins.
        libraryDependencies := Nil,
        libraryDependencies +=
          (SbtMigrationGroupId %% SbtMigrationName % migratorVersion),
        resolvers += Resolver.bintrayRepo("scalacenter", "releases"),
        resolvers += Resolver.bintrayIvyRepo("scalameta", "maven")
      )
  }

  protected def getZincMegaJar(
      stubFor211: Project,
      stubFor212: Project): Def.Initialize[Task[File]] = {
    Def.taskDyn[File] {
      val updateAction = {
        Keys.scalaVersion.value match {
          case Version("11") => Keys.update in stubFor211
          case Version("12") => Keys.update in stubFor212
          case v =>
            val st = state.value
            val logger = st.log
            logger.info(s"Got $v. Only Scala 2.11 or 2.12 are supported.")
            val extracted = Project.extract(st)
            val nextVersion = "2.11.8"
            logger.info(s"Changing to $nextVersion to resolve sbt rewrites.")
            val newScalaVersion = scalaVersion := nextVersion
            extracted.append(List(newScalaVersion), st)
            Keys.update in stubFor211
        }
      }
      Def.task(getJar(updateAction.value, SbtMigrationVersion))
    }
  }
}
