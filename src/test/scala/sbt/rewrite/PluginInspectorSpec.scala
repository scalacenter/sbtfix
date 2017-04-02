package sbt.rewrite

import java.io.File

import org.scalatest.FunSuite

class PluginInspectorSpec extends FunSuite {
  private val loader = getClass.getClassLoader
  private def loadResource(fileName: String): File =
    new File(loader.getResource(fileName).toURI.getPath)

  test("plugin inspector detects all plugin entries") {
    val pluginsFiles = List(loadResource("plugins.sbt"))
    val inspector = new PluginInspector(pluginsFiles)
    val expected = List(SbtPlugin("com.jsuereth", "sbt-pgp", "1.0.0"),
                        SbtPlugin("me.lessis", "bintray-sbt", "0.3.0"),
                        SbtPlugin("com.github.gseitz", "sbt-release", "1.0.3"))
    assertResult(expected)(inspector.discoveredPlugins)
    assert(inspector.pluginLocations.nonEmpty)
  }
}
