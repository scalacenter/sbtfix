/*
rewrite = "scala:sbtfix.SbtOneZeroMigration"
 */
package sbtfix

import sbt._

object SbtOneZeroMigrationTest {
  object `<++=` {
    val key = taskKey[Seq[File]]("Seed.")
    val target = taskKey[Seq[File]]("Target to be reassigned.")
    key := List(new File("."))
    target <++= key
    target <++= (key)
    target <++= (key in ThisBuild)
    target <++= Def.task(key.value)
    target <++= Def.task {
      println("Executing task.")
      (key).value
    }
  }

  object `<+=` {
    val key = taskKey[File]("Single.")
    val target = taskKey[Seq[File]]("Target to be reassigned.")
    key := new File(".")
    target <+= key
    target <+= (key)
    target <+= (key in ThisBuild)
    target <+= Def.task(key.value)
    target.<+=[File](Def.task(key.value))
    target <+= Def.task {
      println("Executing task.")
      (key).value
    }
  }

  object `<<=` {
    import Keys.{compile, name}
    lazy val test1 = taskKey[sbt.inc.Analysis]("Test 1.")
    test1 <<= (compile in Compile)
    lazy val test1b = taskKey[sbt.inc.Analysis]("Test 1b.")
    test1b <<= compile in Compile
    lazy val test2 = taskKey[sbt.inc.Analysis]("Test 2.")
    test2 <<= Def.task { (compile in Compile).value }
    lazy val test3 = settingKey[String]("Test 3.")
    test3 <<= name
    lazy val test4 = settingKey[String]("Test 4.")
    test4 <<= Def.setting { name.value }
    lazy val test5 = taskKey[sbt.inc.Analysis]("Test 5.")
    test5 <<= Def.task {
      println("Executing task.")
      (compile in Compile).value
    }
    lazy val test6 = settingKey[String]("Test 6.")
    test6 <<= Def.setting {
      println("Executing setting.")
      name.value
    }
  }

  object `special<<=` {
    import Keys.run
    val key = inputKey[Unit]("Seed.")
    run <<= key
  }
}
