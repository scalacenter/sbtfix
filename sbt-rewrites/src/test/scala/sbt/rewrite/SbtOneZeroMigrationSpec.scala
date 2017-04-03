package sbt.rewrite

import java.io.File

import org.scalatest.FunSuite

import scalafix.cli.{Cli, ExitStatus, ScalafixOptions}
import scalafix.config.ScalafixConfig
import scalafix.util.FileOps

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
    // Trick Scalafix temporarily until it supports .sbt-ending files
    val file = File.createTempFile("test-file", ".scala", inDir)
    FileOps.writeFile(file, contents)
    file
  }

  private def fixSbtFile(sbtFile: File): ExitStatus = {
    val ctx = SbtContext(Array())
    val config = ScalafixConfig(dialect = scala.meta.dialects.Sbt0137,
                                rewrites = List(SbtOneZeroMigration(ctx)))
    Cli.runOn(
      ScalafixOptions(files = List(sbtFile.getAbsolutePath),
                      inPlace = true,
                      config = Some(config))
    )
  }

  def testSbtRewrite(original: String, expected: String): Unit = {
    val dir = createTempDir
    val sbtFile = createFile(original, dir)
    assert(original != expected, "Original and expected are the same.")
    assert(fixSbtFile(sbtFile) === ExitStatus.Ok)
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
