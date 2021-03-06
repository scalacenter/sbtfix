lazy val seeds = taskKey[Seq[File]]("Seed.")
seeds := List(new File("."))

sourceGenerators in Compile <+= seeds

(sourceGenerators in Compile) <+= seeds

sourceGenerators in Compile <+= Def.task(seeds.value)

(sourceGenerators in Compile) <+= Def.task {
  seeds.value
}

(sourceGenerators in Compile) <+= (Def.task {
  seeds.value
})
