import org.scalajs.linker.interface.ModuleSplitStyle

val scala3Version = "3.7.4"
ThisBuild / scalaVersion := scala3Version

val circeVersion = "0.14.7"
val slinkyVersion = "0.7.5"

lazy val commonSettings = Seq(
  scalaModuleInfo := scalaModuleInfo.value.map(_.withOverrideScalaVersion(true)),
  Test / parallelExecution := false,
  scalacOptions ++= Seq(
    "-encoding",
    "utf-8",
    "-deprecation",
    "-feature",
    "-Wunused:all"
  ),

  // We have a `main` method
  scalaJSUseMainModuleInitializer := true,

  // Emit modules in the most Vite-friendly way.
  // For the best feedback loop with Vite,
  // it is recommended to emit small modules for application code.
  scalaJSLinkerConfig ~= {
    _.withModuleKind(ModuleKind.ESModule)
      .withModuleSplitStyle(
        ModuleSplitStyle.SmallModulesFor(List("scalafui"))
      )
  },
  dependencyOverrides ++= Seq(
    "org.scala-lang" % "scala3-library_3" % scala3Version,
    "org.scala-lang" % "scala3-interfaces" % scala3Version,
    "org.scala-lang" % "tasty-core_3" % scala3Version
  ),
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.8.0",
    "org.scala-js" %%% "scala-js-macrotask-executor" % "1.1.1",
    "me.shadaj" %%% "slinky-web" % slinkyVersion,
    "me.shadaj" %%% "slinky-hot" % slinkyVersion,
    "io.circe" %%% "circe-core" % circeVersion,
    "io.circe" %%% "circe-generic" % circeVersion,
    "io.circe" %%% "circe-parser" % circeVersion,
    "org.typelevel" %%% "cats-effect" % "3.7.1",
    "co.fs2" %%% "fs2-core" % "3.11.0",
    "com.softwaremill.quicklens" %%% "quicklens" % "1.9.7",
    "org.scalatest" %%% "scalatest" % "3.2.19" % Test
  )
)

lazy val scalafui = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)

lazy val exampleTodo = project
  .in(file("examples/todo"))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .dependsOn(scalafui)

lazy val exampleMultipage = project
  .in(file("examples/multipage"))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .dependsOn(scalafui)

lazy val exampleStopwatch = project
  .in(file("examples/stopwatch"))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
  .dependsOn(scalafui)
