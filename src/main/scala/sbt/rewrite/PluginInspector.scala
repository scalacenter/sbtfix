package sbt.rewrite

import java.io.File

import scala.meta._
import scala.meta.dialects.Sbt0137
import scalafix.util.FileOps

case class SbtPlugin(org: String, name: String, version: String)

object SbtPlugin {
  def apply(literals: Seq[Lit]): SbtPlugin = {
    def toString(lit: Lit) = lit.value.asInstanceOf[String]
    literals match {
      case Seq(org: Lit, name: Lit, version: Lit) =>
        SbtPlugin(toString(org), toString(name), toString(version))
      // Perhaps configuration is specified? Protect against it.
      case Seq(org: Lit, name: Lit, version: Lit, _: Lit) =>
        SbtPlugin(toString(org), toString(name), toString(version))
    }
  }
}

class PluginInspector(files: Seq[File]) {
  type InputKeys = Seq[String]
  type KeyOfTasks = Seq[String]

  val sbtRepositories: Seq[coursier.Repository] = {
    import coursier.{Cache, MavenRepository}
    import coursier.ivy.IvyRepository

    val pluginReleases =
      "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/[type]s/[artifact](-[classifier]).[ext]"
    val pluginSnapshots =
      "https://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots/[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/[type]s/[artifact](-[classifier]).[ext]"

    def parseSbtRepo(repo: String): coursier.Repository =
      IvyRepository.parse(repo).leftMap(sys.error).merge

    Seq(
      Cache.ivy2Local,
      MavenRepository("https://repo1.maven.org/maven2", sbtAttrStub = true),
      parseSbtRepo(pluginReleases),
      parseSbtRepo(pluginSnapshots)
    )
  }

  lazy val discoveredPlugins: Seq[SbtPlugin] = {
    if (files.nonEmpty) {
      // Assumes that sbt code is correct and can be compiled by 0.13.x
      val sbtContents = files.map(FileOps.readFile).foldLeft("")(_ ++ _)
      val tree = sbtContents.parse[Source].get
      val pluginEntries = tree.collect {
        case Term.Apply(Term.Name("addSbtPlugin"), Seq(pluginEntry)) =>
          pluginEntry
      }
      val pluginLiterals = pluginEntries.map(_.collect { case l: Lit => l })
      pluginLiterals.map(SbtPlugin.apply)
    } else Nil
  }

  lazy val pluginLocations: Seq[File] = {
    import coursier._
    val attributes = Map("sbtVersion" -> "0.13", "scalaVersion" -> "2.10")
    def toDep(plugin: SbtPlugin): Dependency =
      Dependency(Module(plugin.org, plugin.name, attributes), plugin.version)
    val dependencies = discoveredPlugins.map(toDep).toSet
    val start = Resolution(dependencies)
    val fetch = Fetch.from(sbtRepositories, Cache.fetch())
    val resolution = start.process.run(fetch).run
    val errorsOrArtifacts = scalaz.concurrent.Task
      .gatherUnordered(
        resolution.artifacts.map(Cache.file(_).run)
      )
      .unsafePerformSync
    println(errorsOrArtifacts)
    if (resolution.errors.nonEmpty)
      sys.error(s"Resolution errors: ${resolution.errors.mkString("\n")}")
    else errorsOrArtifacts.flatMap(_.toList)
  }

  def extractInputKeys: InputKeys = {
    ???
  }

  def extractKeyOfTasks: InputKeys = {
    ???
  }
}

object PluginInspector {
  def inspectGlobalSbt: PluginInspector = {
    import better.files._
    val home = sys.props.getOrElse("user.home", "$HOME is not defined.")
    val sbtGlobalFolder = home / ".sbt"
    val allSbtFiles =
      sbtGlobalFolder.listRecursively.filter(_.extension.contains(".sbt"))
    new PluginInspector(allSbtFiles.map(_.toJava).toList)
  }
}
