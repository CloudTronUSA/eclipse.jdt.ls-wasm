# Eclipse JDT LS for Browser WASM

This fork contains a lightweight browser/WASM Java linting build based on Eclipse
JDT compiler internals. It is intended for VS Code Web and other browser-only
hosts where the normal Eclipse JDT Language Server process cannot run.

The implementation is not a full port of the original Eclipse JDT LS runtime.
It reuses ECJ/JDT compiler diagnostics and wraps them in a small browser-safe
language-server surface that can be loaded from a Web Worker.

## What Is Included

- `browser-jdtls/`
  - TeaVM WebAssembly GC build for browser Java diagnostics.
  - ECJ no-codegen compiler diagnostics.
  - In-memory workspace model for a folder of Java files.
  - Minimal JSON-RPC/LSP handler for diagnostics.
  - Standalone Playwright Chromium tests.

- `third_party/teavm/`
  - Vendored patched TeaVM source used to build `browser-jdtls`.
  - The patches add ECJ resource support, classlib shims, and WASM fixes needed
    by this browser build.

- `integrations/vscode-java-web/`
  - The modified VS Code Web extension entry points and tests used to integrate
    this WASM diagnostics engine with Red Hat `vscode-java`.
  - Prebuilt WASM artifacts are included there for the integration fixture.

The original JDT LS source tree is still present in this repository. The browser
linting work lives in the new module and integration folders above.

## Implemented Browser Linting

The browser module currently supports basic Java diagnostics for files and
folders:

- Syntax errors.
- Type mismatch errors.
- Unresolved variables, imports, and types.
- Basic method applicability errors.
- Common ECJ warnings in covered cases, such as unused locals.
- Cross-file type resolution inside the in-memory workspace.
- Revalidation after open/change/close/save/create/delete/rename.
- Basic compiler source/compliance/target settings.
- Package mismatch diagnostics for common source roots.
- Common JDK APIs through synthetic in-memory stubs.

The synthetic JDK coverage includes common pieces of:

- `java.lang`
- `java.io`
- `java.util`
- `java.util.function`
- `java.util.stream`
- `java.util.concurrent`
- `java.nio.file`
- `java.time`
- `java.math`
- `java.net`
- `java.lang.annotation`

## Current Limits

This fork is focused on lightweight linting. It does not currently provide full
JDT LS parity.

Known limits:

- No Maven or Gradle project import.
- No external jar dependency/classpath resolution.
- No annotation processing.
- No full Eclipse workspace/build-path model.
- No full JDK API surface; browser linting uses synthetic JDK stubs.
- No full set of JDT LS language features such as completion, hover, code
  actions, refactoring, debug, or project management.

For the narrowed browser goal, the current build is best described as:

> ECJ-backed Java file/folder linting for browser WebAssembly, with a small
> LSP-compatible diagnostics surface.

## Build Prerequisites

- JDK 21 or compatible modern JDK.
- Maven via the repository `./mvnw` wrapper.
- Node.js and npm for Playwright tests.
- Chromium installed by Playwright or available through Playwright.

## Build Patched TeaVM

`browser-jdtls` depends on the vendored patched TeaVM artifacts. Publish them to
your local Maven repository first:

```sh
cd third_party/teavm
./gradlew :core:publishToMavenLocal :tools:maven:plugin:publishToMavenLocal
./gradlew :classlib:publishToMavenLocal
```

The important TeaVM changes are:

- Include ECJ parser and message resources in the WASM bundle.
- Add `javax.tools` and `javax.lang.model` classlib substitutions.
- Add classlib methods/shims needed by ECJ.
- Add `teavm.wasm.noAsync=true` support for this WASM GC build.
- Fix classlib/runtime issues encountered while compiling ECJ to WASM.

## Build Browser JDT LS

From the repository root:

```sh
./mvnw -q -f browser-jdtls/pom.xml clean process-classes
```

The generated browser artifacts are written to:

```text
browser-jdtls/target/generated/wasm/teavm/classes.wasm
browser-jdtls/target/generated/wasm/teavm/classes.wasm-runtime.js
```

## Run Standalone Browser Tests

Install test dependencies once:

```sh
cd browser-jdtls
npm install
```

Run the standalone Chromium test suite:

```sh
npm test
```

These tests load the TeaVM WASM output in Chromium and call the browser API
directly.

## Browser API

After loading the generated runtime and WASM, the module exposes:

```js
const api = await globalThis.browserJdtLsReady;

const diagnostics = JSON.parse(api.lint(
  "file:///workspace/src/App.java",
  "public class App { int value() { return \"bad\"; } }"
));
```

It also exposes a small JSON-RPC/LSP-like handler:

```js
const messages = JSON.parse(api.handle(JSON.stringify({
  jsonrpc: "2.0",
  method: "textDocument/didOpen",
  params: {
    textDocument: {
      uri: "file:///workspace/src/App.java",
      text: "public class App { int value() { return \"bad\"; } }"
    }
  }
})));
```

Supported messages include:

- `initialize`
- `shutdown`
- `textDocument/didOpen`
- `textDocument/didChange`
- `textDocument/didClose`
- `workspace/didChangeWatchedFiles`
- `workspace/didChangeConfiguration`

Diagnostics are returned as `textDocument/publishDiagnostics` messages.

## VS Code Web Integration

The `integrations/vscode-java-web/` directory contains the Web extension pieces
used with Red Hat `vscode-java`:

- `src/webExtension.ts`
- `src/browserJdtLsWorker.ts`
- `src/generated/browser-jdtls/classes.wasm-runtime.*`
- `resources/browser-jdtls/teavm/classes.wasm`
- `resources/browser-jdtls/teavm/classes.wasm-runtime.js`
- `test-web/browser-jdtls-web.spec.mjs`
- `test-web/playwright.config.mjs`

To use these with a `vscode-java` checkout:

1. Copy `src/webExtension.ts` into the extension `src/` directory.
2. Copy `src/browserJdtLsWorker.ts` into the extension `src/` directory.
3. Copy `src/generated/browser-jdtls/` into the extension `src/generated/`
   directory.
4. Copy `resources/browser-jdtls/` into the extension `resources/` directory.
5. Add a browser extension entry in `package.json`:

```json
"browser": "./dist/webExtension"
```

6. Enable Java activation in web workspaces, for example:

```json
"activationEvents": [
  "onLanguage:java"
]
```

7. Add webpack entries for:

- `src/webExtension.ts` as `dist/webExtension.js`
- `src/browserJdtLsWorker.ts` as `dist/browserJdtLsWorker.js`

8. Build the extension and run the Playwright Web tests.

The integration starts a browser Worker, loads the WASM engine, preloads
workspace `**/*.java` files, and forwards diagnostics through
`vscode-languageclient/browser`.

## Refresh VS Code Web Artifacts

After rebuilding `browser-jdtls`, refresh the integration artifacts:

```sh
cp browser-jdtls/target/generated/wasm/teavm/classes.wasm \
  integrations/vscode-java-web/resources/browser-jdtls/teavm/classes.wasm

cp browser-jdtls/target/generated/wasm/teavm/classes.wasm-runtime.js \
  integrations/vscode-java-web/resources/browser-jdtls/teavm/classes.wasm-runtime.js

cp integrations/vscode-java-web/resources/browser-jdtls/teavm/classes.wasm-runtime.js \
  integrations/vscode-java-web/src/generated/browser-jdtls/classes.wasm-runtime.js
```

When bundling for VS Code Web, the generated runtime must not fall back to Node
filesystem APIs. The current integration runtime contains a guard for that path.

## Verification Status

The current implementation has been verified with Playwright Chromium in two
ways:

- Standalone browser WASM tests for `browser-jdtls`.
- VS Code Web extension tests using the worker integration.

The latest local verification before packaging this fork passed:

- `browser-jdtls`: 29 Chromium tests.
- `vscode-java` Web integration: 12 Chromium tests.

## Repository Notes

This fork intentionally keeps the original JDT LS source tree, but the browser
WASM work is additive. The original server still represents the full desktop
JDT LS implementation; `browser-jdtls` is the browser-oriented diagnostics
subset.

For license and contribution information inherited from Eclipse JDT LS, see
`LICENSE`, `CONTRIBUTING.md`, and the Eclipse project documentation.
