lazy val seed = taskKey[File]("Seed.")
seed := new File(".")

lazy val seeds = taskKey[Seq[File]]("Seed.")
seeds := List(new File("."))

sourceGenerators in Compile <+= seed

(sourceGenerators in Compile) <+= seed

sourceGenerators in Compile <+= Def.task(seed.value)

(sourceGenerators in Compile) <+= Def.task {
  seed.value
}

(sourceGenerators in Compile) <+= (Def.task {
  seed.value
})

sourceGenerators in Compile <++= seeds

(sourceGenerators in Compile) <++= seeds

sourceGenerators in Compile <++= Def.task(seeds.value)

(sourceGenerators in Compile) <++= Def.task {
  seeds.value
}

(sourceGenerators in Compile) <++= (Def.task {
  seeds.value
})
