package sbt.rewrite

import java.io.File

import scalafix.cli.{Cli, CliRunner, ExitStatus}
import scalafix.cli.CliCommand.{PrintAndExit, RunScalafix}
import metaconfig.Configured.{NotOk, Ok}

final class MigrationTool {
  private def migrateBuild(toRewrite: Array[File],
                           sbtContext: SbtContext): ExitStatus = {
    val sbtRewrite = SbtOneZeroMigration(sbtContext)
    val toRewritePaths = toRewrite.map(_.getAbsolutePath).toList
    val options = Cli.default.copy(files = toRewritePaths,
                                   inPlace = true)
    CliRunner.fromOptions(options, sbtRewrite) match {
      case Ok(runner) => runner.run()
      case _: NotOk => ExitStatus.InvalidCommandLineOption
    }
  }

  def migrateBuildFromSbt(toRewrite: Array[File],
                          runtimeSbtInfo: Array[Array[String]]): Int = {
    val settingInfos = runtimeSbtInfo.map(SettingInfo.apply)
    val sbtContext = SbtContext(settingInfos)
    migrateBuild(toRewrite, sbtContext).code
  }
}
