package fix

import sbt._, Keys._

object Sbtmigrationrewrites_v1_Test {

  lazy val target = taskKey[Seq[File]]("Target to be reassigned.")
  lazy val seed = taskKey[Seq[File]]("Seed.")

  target ++= (Def.task {
    println("Executing task.")
    (seed).value
  }).value
}
