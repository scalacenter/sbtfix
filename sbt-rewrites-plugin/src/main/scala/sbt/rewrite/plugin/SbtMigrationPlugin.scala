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
      sbtMigrationJar := getFatJar(sbtMigration211, sbtMigration212).value,
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
        val baseDir = (baseDirectory in ThisBuild).value
        val sbtDir: File = baseDir./("project")
        val sbtFiles = baseDir.*("*.sbt").get
        val toRewrite: Array[File] = (sbtDir +: sbtFiles).toArray
        cachedEntrypoint.invoke(cachedInstance, toRewrite, settingInfos)
      }
    )
  }
}

trait PluginProcessor {

  import scala.Console
  private final val t = s"\t${Console.GREEN}=>${Console.RESET} "

  /**
    * Defines utilities to work around deficiencies in the Manifest pretty
    * printer that makes manifest to produce incorrect stringified Scala types.
    *
    * Note that it trades semantic exactitude with scala syntax correctness.
    * Currently, it avoids invalid pretty printing of type projections, whose
    * projected type is fully qualified. For Scala Meta to parse it, we need to
    * truncate the FQN by a simple type name that is context-dependent.
    */
  object ManifestPrinter {
    def hasPrefix(manifest: Manifest[_]): Boolean = {
      import scala.util.control.Exception.catching
      val clazz = manifest.getClass
      catching(classOf[NoSuchFieldException])
        .opt {
          val prefixField = clazz.getDeclaredField("prefix")
          prefixField.setAccessible(true)
          prefixField.get(manifest).asInstanceOf[Option[_]]
        }
        .flatten
        .isDefined
    }

    def stripPrefixFromClass(clazz: Class[_]): String = {
      val names = clazz.getCanonicalName.split('.')
      names.lastOption.getOrElse(clazz.getName)
    }

    case class Replacement(from: String, to: String)
    def getReplacements(manifest: Manifest[_],
                        acc: List[Replacement]): List[Replacement] = {
      val newReplacements = {
        if (hasPrefix(manifest)) {
          val clazz = manifest.runtimeClass
          val projectionName = s"#${clazz.getName}"
          Replacement(projectionName, s"#${stripPrefixFromClass(clazz)}") :: acc
        } else acc
      }
      manifest.typeArguments.foldLeft(newReplacements) {
        case (replacements, typeArg) =>
          getReplacements(typeArg, replacements)
      }
    }
  }

  /**
    * Process all the settings from projects and autoplugins.
    *
    * This method does repetitive computations just for the sake of
    * correctness -- to make sure no setting is missed. This can be
    * further optimized down the line.
    *
    * @param state The sbt state.
    * @return Array of tuples (represented with arrays) of setting name and type.
    */
  def allSettingInfos(state: State): Array[Array[String]] = {
    def getType(setting: Setting[_]): String = {
      val manifest = setting.key.key.manifest
      val replacements = ManifestPrinter.getReplacements(manifest, Nil)
      val stringifiedType = manifest.toString
      replacements.foldLeft(stringifiedType) {
        case (finalRepr, replacement) =>
          finalRepr.replace(replacement.from, replacement.to)
      }
    }

    val logger = state.log
    val extracted = Project.extract(state)
    val allProjects = extracted.structure.allProjects
    val userProjects = allProjects.filterNot(_.id.contains("sbt-migrator-1"))
    val settingsWithTypes = userProjects.toSet.flatMap {
      (project: ResolvedProject) =>
        logger.info(s"Analyzing keys of project ${project.id}.")
        val projectSettings =
          project.settings.map(s => s.key.key.label -> getType(s))
        val pluginSettings = project.autoPlugins.flatMap { plugin =>
          logger.info(s"${t}Analyzed keys of ${plugin.label}.")
          val pluginSettings0 = plugin.buildSettings.++(plugin.globalSettings)
          pluginSettings0.map(s => s.key.key.label -> getType(s))
        }
        projectSettings.toSet ++ pluginSettings.toSet
    }

    // Converting to arrays to pass them as arguments via reflection
    settingsWithTypes.toList.sorted.map(t => Array(t._1, t._2)).toArray
  }
}

trait ReflectionUtils {
  protected val SbtMigrationProxy = "sbt.rewrite.MigrationTool"
  protected val RunMethod = "migrateBuildFromSbt"

  def getProxyMethod(clazz: Class[_]): reflect.Method = {
    clazz.getDeclaredMethod(
      RunMethod,
      classOf[Array[File]],
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
        resolvers += Resolver.bintrayRepo("scalameta", "maven")
      )
  }

  protected def getFatJar(
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
