lazy val seeds = taskKey[Seq[File]]("Seed.")
seeds := List(new File("."))

resourceGenerators in Compile <+= seeds

(resourceGenerators in Compile) <+= seeds

resourceGenerators in Compile <+= Def.task(seeds.value)

(resourceGenerators in Compile) <+= Def.task {
  seeds.value
}

(resourceGenerators in Compile) <+= (Def.task {
  seeds.value
})
