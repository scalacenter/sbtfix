# Migrate to sbt 1.0
[![Build Status](https://platform-ci.scala-lang.org/api/badges/jvican/sbt-migration-rewrites/status.svg)](https://platform-ci.scala-lang.org/jvican/sbt-migration-rewrites)

*This rewrite library is under heavy development.*

Scalafix rewrites to migrate builds from sbt 0.13.x to 1.0.x.

These rewrites are mainly syntactic and use information available at runtime
to fill in the gap for missing semantic rewrites. `sbt-migration` aims at helping
sbt users port their 0.13.x sbt code to 1.0.x, but it does not promise to do
the full job for them.

The lack of fully semantic rewrites prevent it from doing 100% safe
transformations, so manual intervention may be required in corner cases. 

## Use

Coming soon.

## Rewrites

The migration tool rewrites the following sbt operators: `<<=`, `<+=`, `<++=`.

Migration of tuple enrichment is not supported on purpose.
The proposed official rewrite changes semantics and is unclear until which
extent tuple enrichments should be gone in sbt 1.0.

### Can these rewrites be only syntactic?

No, sbt rewrites may need semantic information to disambiguate which sbt macro
should be executed. Sbt operators change semantics depending on the type of tasks
and settings.

Two examples:
1. Input keys need `.evaluated` instead of `.value`.
2. Keys that store tasks or settings need `.taskValue` instead of `.value`.

### How do they work?

Sbt rewrites cannot be semantic because [Scala Meta is not cross-compiled to Scala 2.10.x series](https://github.com/scalameta/scalameta/issues/295).

To fill in the gap, sbt rewrites make a best-effort to get this semantic
information from sbt at runtime via the Scala reflection API. This type information
is then interpreted on the fly and used to speculate on type-driven rewrites.

The core logic of the rewrites assumes the following:

* Sbt DSL operators are not defined by a third party.
* If they are defined, they are not binary operators.

If those conditions are not met, the migration tool will not rewrite your code
correctly. Note that this misbehaviour is unlikely to happen -- the likelihood
that there exists a library fulfilling those requirements and that you use it in
your sbt code is low.

The sbt runtime analyzer allows the rewrite to get access to all the
present sbt keys and analyze their type, however this information is not
reliable enough to be called "semantic". The reasons are the following:
  
* Manifest pretty printer will produce inaccurate type representations.
  For instance, stringified type projections and type closures don't follow Scala syntax.
* Manifest prints fully qualified names for all names not present in
  Scala jars, but there is no way to check this contract is not broken.
* A key scoped in a project `P` with name `X` and of type `T` may conflict with
  a key scoped in a different project also named `X` but of type `U` if either `T` or `U` or both need special
  handling by the rewrites. This is *definitely not likely* to happen in your
  project.
  
### Feedback

The sbt analyzer will report you all the collected information at runtime.

It looks like:

```
Migrating your sbt 0.13 build file to 1.0...
Analyzing keys of sbt.plugins.CorePlugin.
Analyzing keys of sbt.plugins.Giter8TemplatePlugin.
Analyzing keys of sbt.plugins.IvyPlugin.
Analyzing keys of sbt.plugins.JvmPlugin.
Analyzing keys of sbt.rewrite.plugin.SbtMigrationPlugin.
Analyzing keys of sbt.plugins.JUnitXmlReportPlugin.
Running scalafix...
Sbt runtime analysis found:
	=> Input keys: testQuick, testOnly, runMain, run.
	=> Keys to be `evaluated`: resourceGenerators, sourceGenerators.
	=> Errors parsing .
Running scalafix... (100.00 %, 2 / 2)
```

We can see:

* Plugins that are being analyzed.
* Detected input keys,
* Detected keys that need `evaluated`, and
* Task types failed to parse at runtime.

This information helps you understand what the tool is doing and may shed some
light on potential misbehaviours. I recommend that for a faithful migration, you
double-check if the keys failed to parse are either input keys or they store
tasks.
