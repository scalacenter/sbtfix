lazy val seqKey = taskKey[Seq[File]]("Target to be reassigned.")
lazy val seed = taskKey[Seq[File]]("Seed.")

seqKey := Seq.empty
seed in ThisBuild := List(new File("."))

seqKey <++= seed

seqKey <++= (seed)

seqKey <++= (seed in ThisBuild)

seqKey <++= Def.task(seed.value)

seqKey <++= Def.task {
  println("Executing task.")
  (seed).value
}
