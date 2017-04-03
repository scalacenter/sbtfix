package sbt.rewrite

import java.io.File

import scalafix.cli.{Cli, ExitStatus, ScalafixOptions}
import scalafix.config.ScalafixConfig

final class MigrationTool {
  private def migrateBuild(rootFolder: File,
                           sbtContext: SbtContext): ExitStatus = {
    val sbtRewrite = SbtOneZeroMigration(sbtContext)
    val config = ScalafixConfig(rewrites = List(sbtRewrite))
    Cli.runOn(
      ScalafixOptions(files = List(rootFolder.getAbsolutePath),
                      inPlace = true,
                      config = Some(config))
    )
  }

  def migrateBuildFromSbt(rootFolder: File,
                          runtimeSbtInfo: Array[Array[String]]): Int = {
    val settingInfos = runtimeSbtInfo.map(SettingInfo.apply)
    val sbtContext = SbtContext(settingInfos)
    migrateBuild(rootFolder, sbtContext).code
  }
}
