lazy val seed = taskKey[File]("Seed.")
seed := new File(".")

lazy val seeds = taskKey[Seq[File]]("Seed.")
seeds := List(new File("."))

resourceGenerators in Compile <+= seed

(resourceGenerators in Compile) <+= seed

resourceGenerators in Compile <+= Def.task(seed.value)

(resourceGenerators in Compile) <+= Def.task {
  seed.value
}

(resourceGenerators in Compile) <+= (Def.task {
  seed.value
})

resourceGenerators in Compile <++= seeds

(resourceGenerators in Compile) <++= seeds

resourceGenerators in Compile <++= Def.task(seeds.value)

(resourceGenerators in Compile) <++= Def.task {
  seeds.value
}

(resourceGenerators in Compile) <++= (Def.task {
  seeds.value
})
