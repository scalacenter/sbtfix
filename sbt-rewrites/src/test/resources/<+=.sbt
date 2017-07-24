lazy val seqKey = taskKey[Seq[File]]("Target to be reassigned.")
lazy val seed = taskKey[File]("Seed.")

seqKey := Seq.empty
seed in ThisBuild := new File(".")

seqKey <+= seed

seqKey <+= (seed)

seqKey <+= (seed in ThisBuild)

seqKey <+= Def.task(seed.value)

seqKey.<+=[File](Def.task(seed.value))

seqKey <+= Def.task {
  println("Executing task.")
  (seed).value
}
