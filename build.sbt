import org.scalajs.linker.interface.ModuleSplitStyle

ThisBuild / scalaVersion := "2.13.10"

val circeVersion = "0.14.5"
val slinkyVersion = "0.7.3"

val fastLinkOutputDir = taskKey[String]("output directory for `npm run dev`")
val fullLinkOutputDir = taskKey[String]("output directory for `npm run build`")

lazy val commonSettings = Seq(
  scalacOptions ++= Seq("-encoding", "utf-8", "-deprecation", "-feature"),

  // We have a `main` method
  scalaJSUseMainModuleInitializer := true,

  // Emit modules in the most Vite-friendly way
  scalaJSLinkerConfig ~= {
    _.withModuleKind(ModuleKind.ESModule)
      .withModuleSplitStyle(
        ModuleSplitStyle.SmallModulesFor(List("scalafui"))
      )
  },
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "2.4.0",
    "org.scala-js" %%% "scala-js-macrotask-executor" % "1.0.0",
    "me.shadaj" %%% "slinky-web" % slinkyVersion,
    "me.shadaj" %%% "slinky-hot" % slinkyVersion,
    "io.circe" %%% "circe-core" % circeVersion,
    "io.circe" %%% "circe-generic" % circeVersion,
    "io.circe" %%% "circe-parser" % circeVersion,
    "org.typelevel" %%% "cats-effect" % "3.4.5",
    "org.scalatest" %%% "scalatest" % "3.2.9" % Test
  ),
  fastLinkOutputDir := {
    // Ensure that fastLinkJS has run, then return its output directory
    (Compile / fastLinkJS).value
    (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
      .getAbsolutePath()
  },
  fullLinkOutputDir := {
    // Ensure that fullLinkJS has run, then return its output directory
    (Compile / fullLinkJS).value
    (Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value
      .getAbsolutePath()
  }
)

lazy val scalafui = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(commonSettings)
