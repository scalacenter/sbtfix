package sbtfix

import sbt._

object SbtOneZeroMigrationTest {
  lazy val target = taskKey[Seq[File]]("Target to be reassigned.")
  lazy val single = taskKey[File]("Single.")
  lazy val seed = taskKey[Seq[File]]("Seed.")

  object `<++=` {
    seed := List(new File("."))
    target ++= seed.value
    target ++= (seed).value
    target ++= (seed in ThisBuild).value
    target ++= Def.task(seed.value).value
    target ++= (Def.task {
      println("Executing task.")
      (seed).value
    }).value
  }

  object `<+=` {
    single := new File(".")
    target += single.value
    target += (single).value
    target += (single in ThisBuild).value
    target += Def.task(single.value).value
    target.+=[File](Def.task(single.value).value)
    target += (Def.task {
      println("Executing task.")
      (single).value
    }).value
  }

  object `<<=` {
    import Keys.{compile, name}
    lazy val test1 = taskKey[sbt.inc.Analysis]("Test 1.")
    test1 := (compile in Compile).value
    lazy val test1b = taskKey[sbt.inc.Analysis]("Test 1b.")
    test1b := (compile in Compile).value
    lazy val test2 = taskKey[sbt.inc.Analysis]("Test 2.")
    test2 := (Def.task { (compile in Compile).value }).value
    lazy val test3 = settingKey[String]("Test 3.")
    test3 := name.value
    lazy val test4 = settingKey[String]("Test 4.")
    test4 := (Def.setting { name.value }).value
    lazy val test5 = taskKey[sbt.inc.Analysis]("Test 5.")
    test5 := (Def.task {
      println("Executing task.")
      (compile in Compile).value
    }).value
    lazy val test6 = settingKey[String]("Test 6.")
    test6 := (Def.setting {
      println("Executing setting.")
      name.value
    }).value
  }
}
