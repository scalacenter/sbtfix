package sbtfix

import scala.meta._
import scalafix.testkit._

class SbtOneZeroMigrationTests
    extends SemanticRewriteSuite(
      Database.load(Classpath(AbsolutePath(BuildInfo.inputClassDirectory))),
      AbsolutePath(BuildInfo.inputSourceRoot),
      Seq(AbsolutePath(BuildInfo.outputSourceRoot))
    ) {
  runAllTests()
}
