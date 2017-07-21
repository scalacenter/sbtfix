package sbt.rewrite

import java.io.File

import scalafix.cli.{Cli, CliRunner, ExitStatus}
import scalafix.cli.CliCommand.{PrintAndExit, RunScalafix}
import metaconfig.Configured.{NotOk, Ok}

final class MigrationTool {
  private def migrateBuild(rootFolder: File,
                           sbtContext: SbtContext): ExitStatus = {
    val sbtRewrite = SbtOneZeroMigration(sbtContext)
    val options = Cli.default.copy(files = List(rootFolder.getAbsolutePath),
                                   inPlace = true)
    CliRunner.fromOptions(options, sbtRewrite) match {
      case Ok(runner) => runner.run()
      case _: NotOk => ExitStatus.InvalidCommandLineOption
    }
  }

  def migrateBuildFromSbt(rootFolder: File,
                          runtimeSbtInfo: Array[Array[String]]): Int = {
    val settingInfos = runtimeSbtInfo.map(SettingInfo.apply)
    val sbtContext = SbtContext(settingInfos)
    migrateBuild(rootFolder, sbtContext).code
  }
}
