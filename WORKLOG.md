# Work log

## Generate an empty Vite project

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

## Introduce Scala.js

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

