# VS Code Java Web Integration

This directory contains the browser-side integration files used to connect
`browser-jdtls` to Red Hat `vscode-java` in VS Code Web.

It is not a full copy of `vscode-java`. Copy these files into a `vscode-java`
checkout and update that extension's `package.json` and `webpack.config.js`.

## Files

- `src/webExtension.ts`
  - Web extension activation entry.
  - Starts the browser JDT LS worker.
  - Preloads workspace Java files.
  - Forwards save/create/delete/rename events.

- `src/browserJdtLsWorker.ts`
  - Browser Worker entry.
  - Loads TeaVM runtime and WASM.
  - Sends and receives JSON-RPC/LSP-shaped messages.

- `src/generated/browser-jdtls/classes.wasm-runtime.*`
  - TeaVM runtime files used by the worker bundle.

- `resources/browser-jdtls/teavm/classes.wasm`
  - Current generated browser JDT LS WASM artifact.

- `resources/browser-jdtls/teavm/classes.wasm-runtime.js`
  - Current generated TeaVM runtime artifact.

- `test-web/`
  - Playwright Chromium tests for VS Code Web.

## Required `vscode-java` Package Changes

Add a browser entry:

```json
"browser": "./dist/webExtension"
```

Enable Java activation in web:

```json
"activationEvents": [
  "onLanguage:java"
]
```

Set virtual workspace support to true if the test target is VS Code Web:

```json
"capabilities": {
  "virtualWorkspaces": true
}
```

Add test dependencies:

```json
"@playwright/test": "^1.60.0",
"@vscode/test-web": "^0.0.80"
```

## Required Webpack Entries

Add two web-worker-targeted bundles:

- `src/webExtension.ts` -> `dist/webExtension.js`
- `src/browserJdtLsWorker.ts` -> `dist/browserJdtLsWorker.js`

Use `target: "webworker"` and keep `vscode` external for the extension bundle.
The worker bundle imports `src/generated/browser-jdtls/classes.wasm-runtime.js`.

## Runtime Guard

TeaVM's generated runtime can contain a Node filesystem fallback. In VS Code Web
that path must not be used. The copied generated runtime currently contains this
guard:

```js
async function W() {
  throw new Error("Node filesystem is unavailable in VS Code Web");
}
```

Reapply that guard after regenerating the TeaVM runtime.
