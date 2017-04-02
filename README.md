# Rewrites for sbt 1.0
[![Build Status](https://platform-ci.scala-lang.org/api/badges/jvican/sbt-migration-rewrites/status.svg)](https://platform-ci.scala-lang.org/jvican/sbt-migration-rewrites)


Set of syntactic Scalafix rewrites that migrate builds from sbt 0.13.x to 1.0.x.

These rewrites are only syntactic and sometimes speculative.
Semantic rewrites are not possible because Scala Meta does not cross-compile to
Scala 2.10. The provided rewrites may require manual intervention in some corner
cases. They aim at helping sbt users rather than doing the job for them.

## Use

Coming soon. Scalafix is not yet able to rewrite sbt files.

## Rewrites

The migration tool rewrites the following sbt operators: `<<=`, `<+=`, `<++=`.

It does not provide rewrites for tuple enrichments because their `.value`
version changes semantics.
  
### Corner cases

For instance, sbt settings or tasks that store themselves tasks or settings
are not rewritten correctly [because they need `.taskValue`](https://github.com/sbt/sbt/issues/2818)
instead of the regular `.value`. This case may be fully supported with some
potential workarounds in the future.

An example of these keys are `sourceGenerators` and `resourceGenerators` that
have type `SettingKey[Seq[Task[Seq[File]]]]`. However, they are special cased
in the rewrites, so they will work. Keys with similar type coming from sbt
plugins won't be correctly rewritten and will cause a sbt compilation error.
