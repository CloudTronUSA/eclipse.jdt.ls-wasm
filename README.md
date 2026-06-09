# Eclipse JDT LS Web

This fork adds a lightweight browser-oriented Java linter built from Eclipse ECJ
and TeaVM.

The web module is `org.eclipse.jdt.ls.web`. It is intentionally focused on
linting Java source text and related files in a folder. It can be compiled to
WASM or JavaScript. It is not the desktop JDT LS server and it does not include
editor integration, Maven/Gradle import, debugging, completion, hover, code
actions, or a web app.

## Components

- `org.eclipse.jdt.ls.web`
  - Java source for the browser/WASM linter.
  - Public package: `org.eclipse.jdt.ls.web`.
  - Internal TeaVM build glue: `org.eclipse.jdt.ls.web.internal.teavm`.
  - Internal resource selection: `org.eclipse.jdt.ls.web.internal.resources`.
  - Generated WASM output: `org.eclipse.jdt.ls.web/target/generated/wasm/teavm/`.
  - Generated JS output: `org.eclipse.jdt.ls.web/target/generated/js/teavm/`.

- `third_party/teavm`
  - Vendored patched TeaVM compiler/runtime sources required to build the
    WASM/JS artifacts.

## What Is Implemented

- ECJ-backed diagnostics for Java files.
- In-memory folder source model, so files can resolve each other.
- Basic LSP-shaped handling for initialize, open/change/close, watched file
  changes, configuration changes, and publish diagnostics.
- Browser-bundled JDK API signatures generated from the build JDK's `ct.sym`,
  covering the Java/JDK standard library signatures available for Java 17.
  These are compile-time signatures for linting, not runtime implementations.
- Processing Java mode for `.pde` sketches:
  - provide an entrypoint PDE file
  - provide any additional PDE files
  - the linter generates a Processing-style Java sketch class extending
    `processing.core.PApplet`
  - Processing core API signatures are resolved from `org.processing:core`

## Build TeaVM

The web module expects the patched TeaVM artifacts to be available in the local
Maven repository. From the vendored TeaVM tree:

```sh
cd third_party/teavm
./gradlew :core:publishToMavenLocal :classlib:publishToMavenLocal :jso:core:publishToMavenLocal :jso:apis:publishToMavenLocal :tools:maven:plugin:publishToMavenLocal
```

## Build The Web Linter

From the repository root:

```sh
./mvnw -f org.eclipse.jdt.ls.web/pom.xml process-classes
```

Outputs:

```text
org.eclipse.jdt.ls.web/target/generated/wasm/teavm/classes.wasm
org.eclipse.jdt.ls.web/target/generated/wasm/teavm/classes.wasm-runtime.js
org.eclipse.jdt.ls.web/target/generated/js/teavm/classes.js
```

## API

The TeaVM module exports:

```java
org.eclipse.jdt.ls.web.WebJdtLs.lint(String uri, String source)
org.eclipse.jdt.ls.web.WebJdtLs.lintProcessing(String entrypointUri, String entrypointSource, String additionalPdesJson)
org.eclipse.jdt.ls.web.WebJdtLs.handle(String payload)
```

`lint` returns a JSON array of diagnostics for one Java source.

`lintProcessing` expects `additionalPdesJson` in this shape:

```json
{
  "sources": [
    { "uri": "file:///Sketch/OtherTab.pde", "text": "..." }
  ]
}
```

`lintProcessing` returns diagnostics with an added `uri` field so callers can
place diagnostics in the original PDE tab. The entrypoint and each additional
PDE source are mapped back to their own URI and line space.

`handle` accepts LSP-shaped JSON payloads. The Processing-specific method is:

```json
{
  "jsonrpc": "2.0",
  "method": "java/webJdtLs/processingSketch",
  "params": {
    "entrypointUri": "file:///Sketch/Sketch.pde",
    "entrypointText": "void setup() { size(100, 100); }",
    "sources": [
      { "uri": "file:///Sketch/OtherTab.pde", "text": "..." }
    ]
  }
}
```

The Processing handler returns one `textDocument/publishDiagnostics` message per
original PDE URI, including empty diagnostic arrays for tabs that should be
cleared.

## Limits

This is a lightweight linter. It does not provide full desktop JDT LS behavior
or full JDT project modeling. There is no Maven or Gradle support, no external
server, and no editor-specific integration in this repository.
