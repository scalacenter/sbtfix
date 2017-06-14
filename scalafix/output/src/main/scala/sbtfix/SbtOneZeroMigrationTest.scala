package sbtfix

import sbt._

object SbtOneZeroMigrationTest {
  object `<++=` {
    val key = taskKey[Seq[File]]("Seed.")
    val target = taskKey[Seq[File]]("Target to be reassigned.")
    key := List(new File("."))
    target ++= key.value
    target ++= (key).value
    target ++= (key in ThisBuild).value
    target ++= Def.task(key.value).value
    target ++= (Def.task {
      println("Executing task.")
      (key).value
    }).value
  }

  object `<+=` {
    val key = taskKey[File]("Single.")
    val target = taskKey[Seq[File]]("Target to be reassigned.")
    key := new File(".")
    target += key.value
    target += (key).value
    target += (key in ThisBuild).value
    target += Def.task(key.value).value
    target.+=[File](Def.task(key.value).value)
    target += (Def.task {
      println("Executing task.")
      (key).value
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

  object `special<<=` {
    import Keys.run
    val key = inputKey[Unit]("Seed.")
    run := key.value
  }
}
