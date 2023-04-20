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
