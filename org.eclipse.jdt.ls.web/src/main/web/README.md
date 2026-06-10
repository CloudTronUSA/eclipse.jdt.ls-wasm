# eclipse-jdt-ls-web

Browser-targeted Eclipse JDT LS web linter built with TeaVM.

```js
import { load } from "eclipse-jdt-ls-web";

const jdtls = await load();
const diagnosticsJson = jdtls.lint("file:///Example.java", "class Example {}");
```

`load()` tries the WebAssembly GC build first and falls back to the JavaScript
build if the browser cannot load WebAssembly GC or JSPI-dependent artifacts.
The returned `target` is `"wasm"` or `"js"`.

The package expects its included `wasm/` and `js/` directories to be served next
to `web-jdt-ls.js`. Override paths with `baseUrl`, `wasmRuntimeUrl`, `wasmUrl`,
or `jsUrl` when bundling or serving assets from a CDN.
