package sbt.rewrite

import java.io.File

import org.scalatest.FunSuite

import scalafix.cli.{Cli, CliRunner, ExitStatus}
import scalafix.cli.CliCommand.{PrintAndExit, RunScalafix}
import scalafix.internal.util.FileOps
import metaconfig.Configured.{NotOk, Ok}

class SbtOneZeroMigrationSpec extends FunSuite {
  object SbtFiles {
    private val loader = getClass.getClassLoader
    private def loadResource(fileName: String): String =
      FileOps.readFile(loader.getResource(fileName).toURI.getPath)

    val `<<=Test`: String = loadResource("<<=.sbt")
    val `<<=Expected`: String = loadResource("<<=.sbt.expected")

    val `<+=Test`: String = loadResource("<+=.sbt")
    val `<+=Expected`: String = loadResource("<+=.sbt.expected")

    val `<++=Test`: String = loadResource("<++=.sbt")
    val `<++=Expected`: String = loadResource("<++=.sbt.expected")

    val sourceGeneratorsTest: String = loadResource("source-generators.sbt")
    val sourceGeneratorsExpected: String = loadResource(
      "source-generators.sbt.expected")

    val resourceGeneratorsTest: String = loadResource(
      "resource-generators.sbt")
    val resourceGeneratorsExpected: String = loadResource(
      "resource-generators.sbt.expected")

    val evaluatedTest: String = loadResource("evaluated-tasks.sbt")
    val evaluatedExpected: String = loadResource(
      "evaluated-tasks.sbt.expected")

    val officialExample: String = loadResource("example.sbt")
    val officialExampleExpected: String = loadResource("example.sbt.expected")
  }

  private def createTempDir: File = {
    val dir = File.createTempFile("project/src/main/scala", "sbt")
    dir.delete()
    dir.mkdirs()
    assert(dir.isDirectory)
    dir
  }

  private def createFile(contents: String, inDir: File): File = {
    val file = File.createTempFile("test-file", ".sbt", inDir)
    FileOps.writeFile(file, contents)
    file
  }

  object DefaultSbtKeys {
    val sourceGen =
      Array("sourceGenerators", "sbt.Task[Seq[sbt.Task[Seq[File]]]]")
    val resourceGen =
      Array("resourceGenerators", "sbt.Task[Seq[sbt.Task[Seq[File]]]]")
    val run = Array("run", "sbt.InputTask[Unit]")
    val runMain = Array("runMain", "sbt.InputTask[Unit]")
    val testOnly = Array("testOnly", "sbt.InputTask[Unit]")
    val testQuick = Array("testQuick", "sbt.InputTask[Unit]")
    val all = Array(run, runMain, testOnly, testQuick, sourceGen, resourceGen)
  }

  private def fixSbtFile(sbtFile: File): (Interpreted, ExitStatus) = {
    // Default keys have to stay for `SbtOneZeroMigrationSpec` to work for now
    val settingInfos = DefaultSbtKeys.all.map(SettingInfo.apply)
    val ctx = SbtContext(settingInfos)
    val sbtRewrite = SbtOneZeroMigration(ctx)
    val options = Cli.default.copy(files = List(sbtFile.getAbsolutePath),
                                   inPlace = true)
    ctx.interpretContext -> (CliRunner.fromOptions(options, sbtRewrite) match {
      case Ok(runner) => runner.run()
      case NotOk(err) => fail(err.msg)
    })
  }

  final val defaultKeyOfTasks = List("sourceGenerators", "resourceGenerators")
  final val defaultInputKeys = List("run", "runMain", "testOnly", "testQuick")

  def testSbtRewrite(original: String, expected: String): Unit = {
    val dir = createTempDir
    val sbtFile = createFile(original, dir)
    assert(original != expected, "Original and expected are the same.")
    val (sbtRuntime, exitStatus) = fixSbtFile(sbtFile)
    assert(exitStatus === ExitStatus.Ok)
    assert(sbtRuntime.keyOfTasks.sorted === defaultKeyOfTasks.sorted)
    assert(sbtRuntime.inputKeys.sorted === defaultInputKeys.sorted)
    assert(sbtRuntime.failedSignatures.isEmpty)
    assertResult(expected.trim)(FileOps.readFile(sbtFile).trim)
  }

  test("fix <<=") {
    testSbtRewrite(SbtFiles.`<<=Test`, SbtFiles.`<<=Expected`)
  }

  test("fix <+=") {
    testSbtRewrite(SbtFiles.`<+=Test`, SbtFiles.`<+=Expected`)
  }

  test("fix <++=") {
    testSbtRewrite(SbtFiles.`<++=Test`, SbtFiles.`<++=Expected`)
  }

  test("fix sourceGenerators") {
    testSbtRewrite(SbtFiles.sourceGeneratorsTest,
                   SbtFiles.sourceGeneratorsExpected)
  }

  test("fix resourceGenerators") {
    testSbtRewrite(SbtFiles.resourceGeneratorsTest,
                   SbtFiles.resourceGeneratorsExpected)
  }

  test("fix input tasks that need evaluated") {
    testSbtRewrite(SbtFiles.evaluatedTest, SbtFiles.evaluatedExpected)
  }

  /*  test("fix example in official docs") {
    val dir = createTempDir
    val sbtFile = createFile(SbtFiles.officialExample, dir)
    fixSbtFile(sbtFile)
    assertResult(SbtFiles.officialExampleExpected.trim)(
      FileOps.readFile(sbtFile).trim)
  }*/
}
