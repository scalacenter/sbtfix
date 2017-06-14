package sbtfix

import sbt._, Keys._

object SbtOneZeroMigrationTest {

  lazy val target = taskKey[Seq[File]]("Target to be reassigned.")
  lazy val seed = taskKey[Seq[File]]("Seed.")

  target ++= (Def.task {
    println("Executing task.")
    (seed).value
  }).value
}
