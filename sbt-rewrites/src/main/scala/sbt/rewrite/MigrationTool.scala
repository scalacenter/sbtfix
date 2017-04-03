package sbt.rewrite

import java.io.File

import scalafix.cli.{Cli, ScalafixOptions}
import scalafix.config.ScalafixConfig

final class MigrationTool {
  private def migrateBuild(rootFolder: File, sbtContext: SbtContext): Int = {
    val sbtRewrite = SbtOneZeroMigration(sbtContext)
    val config = ScalafixConfig(dialect = scala.meta.dialects.Sbt0137,
                                rewrites = List(sbtRewrite))
    Cli.runOn(
      ScalafixOptions(files = List(rootFolder.getAbsolutePath),
                      inPlace = true,
                      config = Some(config))
    )
  }

  def migrateBuildFromSbt(rootFolder: File,
                          keyOfTasks: Array[String],
                          inputKeys: Array[String]): Int = {
    val sbtContext = SbtContext(keyOfTasks, inputKeys)
    migrateBuild(rootFolder, sbtContext)
  }
}
