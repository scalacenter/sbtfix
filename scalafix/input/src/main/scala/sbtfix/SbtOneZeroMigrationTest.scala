/* ONLY
rewrite = "scala:sbtfix.SbtOneZeroMigration"
 */
package sbtfix

import sbt._

object SbtOneZeroMigrationTest {
  lazy val target = taskKey[Seq[File]]("Target to be reassigned.")
  lazy val single = taskKey[File]("Single.")
  lazy val seed = taskKey[Seq[File]]("Seed.")

  object `<++=` {
    seed := List(new File("."))
    target <++= seed
    target <++= (seed)
    target <++= (seed in ThisBuild)
    target <++= Def.task(seed.value)
    target <++= Def.task {
      println("Executing task.")
      (seed).value
    }
  }

  object `<+=` {
    single := new File(".")
    target <+= single
    target <+= (single)
    target <+= (single in ThisBuild)
    target <+= Def.task(single.value)
    target.<+=[File](Def.task(single.value))
    target <+= Def.task {
      println("Executing task.")
      (single).value
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
}
