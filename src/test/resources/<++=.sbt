lazy val target = taskKey[Seq[File]]("Target to be reassigned.")
lazy val seed = taskKey[Seq[File]]("Seed.")
seed := List(new File("."))

target <++= seed

target <++= (seed)

target <++= (seed in ThisBuild)

target <++= Def.task(seed.value)

target <++= Def.task {
  println("Executing task.")
  (seed).value
}
