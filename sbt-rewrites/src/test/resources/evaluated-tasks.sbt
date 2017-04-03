lazy val seed = inputKey[Unit]("Seed.")
seed := {}

run <<= seed

run <<= (seed in ThisProject)

run <<= seed in ThisProject

run <<= Def.inputTask { seed.value }

run <<= Def.inputTask { (seed).value }

run <<= Def.inputTask {
  println("Executing task.")
  seed.value
}

lazy val seed2 = inputKey[Unit]("Seed 2.")
seed2 := {}

runMain <<= seed2

runMain <<= (seed2 in ThisProject)

runMain <<= seed2 in ThisProject

runMain <<= Def.inputTask { seed2.value }

runMain <<= Def.inputTask { (seed2).value }

runMain <<= Def.inputTask {
  println("Executing task.")
  seed2.value
}

lazy val seed3 = inputKey[Unit]("Seed 3.")
seed3 := {}

testOnly <<= seed3

testOnly <<= (seed3 in ThisProject)

testOnly <<= seed3 in ThisProject

testOnly <<= Def.inputTask { seed3.value }

testOnly <<= Def.inputTask { (seed3).value }

testOnly <<= Def.inputTask {
  println("Executing task.")
  seed3.value
}

lazy val seed4 = inputKey[Unit]("Seed 4.")
seed4 := {}

testQuick <<= seed4

testQuick <<= (seed4 in ThisProject)

testQuick <<= seed4 in ThisProject

testQuick <<= Def.inputTask { seed4.value }

testQuick <<= Def.inputTask { (seed4).value }

testQuick <<= Def.inputTask {
  println("Executing task.")
  seed4.value
}
