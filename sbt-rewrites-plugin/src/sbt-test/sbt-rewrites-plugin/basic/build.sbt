lazy val root = project.in(file(".")).aggregate(p1)
lazy val p1 = project.settings(scalaVersion := "2.11.8")

// This is the target of the sbt rewrite
lazy val test1 = taskKey[sbt.inc.Analysis]("Test 1.")
test1 <<= (compile in Compile)
