# Work log

## Scala.js and Vite

* [Getting Started with Scala\.js and Vite \- Scala\.js](https://www.scala-js.org/doc/tutorial/scalajs-vite.html)
* [sjrd/scalajs\-sbt\-vite\-laminar\-chartjs\-example at scalajs\-vite\-end\-state](https://github.com/sjrd/scalajs-sbt-vite-laminar-chartjs-example/tree/scalajs-vite-end-state)
* [Getting Started with Scala\.js and Vite \| Let´s talk about Scala 3 \- YouTube](https://www.youtube.com/watch?v=dv7fPmgFTNA)

### Generate an empty Vite project

```shell
$ node -v
v18.16.0

$ yarn -v
1.22.19
```

Scaffold a Vite project:
```shell
$ yarn create vite scalafui
Select a framework: › Vanilla
Select a variant: › JavaScript
```

Move the generated files to the top directory:
```shell
$ mv -f scalafui/{.,}* .
$ rm -rf scalafui
```

Test:
```shell
$ yarn
$ yarn run dev
```

### Introduce Scala.js

1. Add Scala.js specific entries to `.gitignore`
2. Add project settings and build.sbt
    * `project/build.properties`
    * `project/plugins.sbt`
    * `build.sbt`
3. Add Scala.js source files
    * `src/main/scala/scalafui/FunctionalUI.scala`
    * `src/main/scala/scalafui/Main.scala`
4. Add npm dependencies (React-related) in `package.json`
5. Add `vite.config.js`
6. Connect the Scala.js output to Vite
    * Change the content of `main.js` to `import '@linkOutputDir/main.js'`
    * Delete the files imported in the old `main.js`
        * `style.css`
        * `javascript.svg`
        * `counter.js`
        * `public/vite.svg`
7. Test the app
    * `$ yarn`
    * `sbt> ~fastLinkJS`
    * `$ yarn run dev`

### Sourcemap errors 

With Vite v4.2.2, sourcemap errors will be output in the console like below when launching a dev server.

```
Sourcemap for "/path/to/scalafui/target/scala-2.13/scalafui-fastopt/scalafui.Main.js" points to missing source files
```

According to the debug log output by `DEBUG="vite:sourcemap" yarn dev`, Vite doesn't seem to properly handle absolute source URLs (starting with `file:`, `https:`) in *.map files. It treats sources as relative paths from the source directory unconditionally like:

```
vite:sourcemap   /path/to/scalafui/target/scala-2.13/scalafui-fastopt/https:/raw.githubusercontent.com/scala-js/scala-js/v1.13.1/library-aux/src/main/scala/scala/runtime/Statics.scala
```

