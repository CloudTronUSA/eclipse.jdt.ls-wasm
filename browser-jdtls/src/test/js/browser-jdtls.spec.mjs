import { test, expect } from '@playwright/test';

function asMessages(response) {
  return Array.isArray(response) ? response : [response];
}

test('ECJ diagnostics run in Chromium through TeaVM WebAssembly GC', async ({ page }) => {
  const messages = [];
  page.on('console', message => messages.push(message.text()));
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/App.java', 'public class App { void m() { int x = ; } }'));
  });
  expect(diagnostics.length).toBeGreaterThan(0);
  expect(diagnostics[0].source).toBe('Java');
  expect(diagnostics[0].severity).toBe(1);
  expect(diagnostics[0].message).toContain('Syntax error');
  expect(messages).not.toContain(expect.stringContaining('Failed to load'));
});

test('LSP-shaped endpoint publishes diagnostics', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/App.java',
          text: 'public class App { void m() { int x = ; } }'
        }
      }
    })));
  });
  const messages = asMessages(response);
  expect(messages.length).toBe(1);
  expect(messages[0].method).toBe('textDocument/publishDiagnostics');
  expect(messages[0].params.uri).toBe('file:///workspace/src/App.java');
  expect(messages[0].params.diagnostics.length).toBeGreaterThan(0);
});

test('LSP endpoint decodes JSON unicode escapes before diagnostics', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.handle('{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"file:///workspace/src/Caf\\u00e9.java","text":"class EscapeUse { String caf\\u00e9() { return 1; } }"}}}'));
  });
  const messages = asMessages(response);
  const diagnostics = messages[0].params.diagnostics;
  const mismatch = diagnostics.find(diagnostic => diagnostic.message === 'Type mismatch: cannot convert from int to String');
  expect(messages[0].method).toBe('textDocument/publishDiagnostics');
  expect(messages[0].params.uri).toBe('file:///workspace/src/Café.java');
  expect(mismatch).toBeTruthy();
});

test('LSP endpoint resolves types from other open Java files', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/demo/Helper.java',
          text: 'package demo; public class Helper { static int twice(int value) { return value * 2; } }'
        }
      }
    }));
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/demo/App.java',
          text: 'package demo; public class App { int value() { return Helper.twice("bad"); } }'
        }
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/App.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(response.method).toBe('textDocument/publishDiagnostics');
  expect(response.params.uri).toBe('file:///workspace/src/demo/App.java');
  expect(messages).toContain('The method twice(int) in the type Helper is not applicable for the arguments (String)');
  expect(messages).not.toContain('Helper cannot be resolved to a type');
  expect(messages).not.toContain('Helper cannot be resolved to a variable');
});

test('LSP endpoint resolves types from preloaded unopened workspace files', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri: 'file:///workspace/src/demo/util/Helper.java',
        text: 'package demo.util; public class Helper { public static int twice(int value) { return value * 2; } }'
      }
    }));
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/demo/App.java',
          text: 'package demo; import demo.util.Helper; public class App { int value() { return Helper.twice("bad"); } }'
        }
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/App.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('The method twice(int) in the type Helper is not applicable for the arguments (String)');
  expect(messages).not.toContain('demo.util.Helper cannot be resolved to a type');
  expect(messages).not.toContain('Helper cannot be resolved to a type');
  expect(messages).not.toContain('Helper cannot be resolved to a variable');
});

test('LSP endpoint resolves secondary top-level types from workspace files', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri: 'file:///workspace/src/demo/Types.java',
        text: 'package demo; class InternalHelper { static int value(InternalValue value) { return value.number(); } } class InternalValue { int number() { return 1; } }'
      }
    }));
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/demo/App.java',
          text: 'package demo; public class App { int value() { return InternalHelper.value(new InternalValue()); } }'
        }
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/App.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).not.toContain('InternalHelper cannot be resolved to a type');
  expect(messages).not.toContain('InternalValue cannot be resolved to a type');
  expect(messages).not.toContain('InternalHelper cannot be resolved to a variable');
});

test('LSP endpoint publishes diagnostics for preloaded unopened workspace files', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri: 'file:///workspace/src/demo/Broken.java',
        text: 'package demo; public class Broken { int value() { return "bad"; } }'
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/Broken.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(response.method).toBe('textDocument/publishDiagnostics');
  expect(messages).toContain('Type mismatch: cannot convert from String to int');
});

test('LSP endpoint reports package declarations that do not match source roots', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/project/src/main/java/com/example/App.java',
          text: 'package wrong.place; public class App {}'
        }
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/App.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(messages.some(message => message.includes('declared package') && message.includes('wrong.place') && message.includes('com.example'))).toBe(true);
});

test('LSP endpoint restores workspace diagnostics when an edited document closes', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const uri = 'file:///workspace/src/demo/App.java';
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri,
        text: 'package demo; public class App { int value() { return "bad"; } }'
      }
    }));
    const opened = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri,
          text: 'package demo; public class App { int value() { return 1; } }'
        }
      }
    })));
    const closed = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didClose',
      params: {
        textDocument: { uri }
      }
    })));
    return {
      opened: (Array.isArray(opened) ? opened : [opened])
        .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message),
      closed: (Array.isArray(closed) ? closed : [closed])
        .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message)
    };
  });

  expect(result.opened).not.toContain('Type mismatch: cannot convert from String to int');
  expect(result.closed).toContain('Type mismatch: cannot convert from String to int');
});

test('LSP endpoint keeps unsaved open buffers separate from saved workspace sources', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const uri = 'file:///workspace/src/demo/App.java';
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri,
        text: 'package demo; public class App { int value() { return 1; } }'
      }
    }));
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri,
          text: 'package demo; public class App { int value() { return "unsaved"; } }'
        }
      }
    }));
    const savedWhileOpen = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri,
        text: 'package demo; public class App { int value() { return 2; } }'
      }
    })));
    const closed = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didClose',
      params: {
        textDocument: { uri }
      }
    })));
    return {
      savedWhileOpen: (Array.isArray(savedWhileOpen) ? savedWhileOpen : [savedWhileOpen])
        .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message),
      closed: (Array.isArray(closed) ? closed : [closed])
        .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message)
    };
  });

  expect(result.savedWhileOpen).toContain('Type mismatch: cannot convert from String to int');
  expect(result.closed).not.toContain('Type mismatch: cannot convert from String to int');
});

test('LSP endpoint removes deleted workspace sources and revalidates dependents', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const helperUri = 'file:///workspace/src/demo/util/Helper.java';
    const appUri = 'file:///workspace/src/demo/App.java';
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri: helperUri,
        text: 'package demo.util; public class Helper { public static int twice(String value) { return value.length(); } }'
      }
    }));
    const opened = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: appUri,
          text: 'package demo; import demo.util.Helper; public class App { int value() { return Helper.twice("ok"); } }'
        }
      }
    })));
    const removed = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/removeWorkspaceSource',
      params: {
        uri: helperUri
      }
    })));
    const openedMessages = (Array.isArray(opened) ? opened : [opened])
      .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message);
    const removedMessages = (Array.isArray(removed) ? removed : [removed])
      .find(message => message.params.uri.endsWith('/App.java')).params.diagnostics.map(diagnostic => diagnostic.message);
    const helperMessages = (Array.isArray(removed) ? removed : [removed])
      .find(message => message.params.uri.endsWith('/Helper.java')).params.diagnostics.map(diagnostic => diagnostic.message);
    return { openedMessages, removedMessages, helperMessages };
  });

  expect(result.openedMessages).not.toContain('Helper cannot be resolved');
  expect(result.removedMessages).toContain('The import demo.util cannot be resolved');
  expect(result.removedMessages).toContain('Helper cannot be resolved');
  expect(result.helperMessages).toEqual([]);
});

test('LSP endpoint handles standard watched-file create, change, and delete events', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const helperUri = 'file:///workspace/src/demo/util/Helper.java';
    const appUri = 'file:///workspace/src/demo/App.java';
    const created = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeWatchedFiles',
      params: {
        changes: [{
          uri: helperUri,
          type: 1,
          text: 'package demo.util; public class Helper { public static int twice(int value) { return value * 2; } }'
        }]
      }
    })));
    const opened = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: appUri,
          text: 'package demo; import demo.util.Helper; public class App { int value() { return Helper.twice("bad"); } }'
        }
      }
    })));
    const changed = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeWatchedFiles',
      params: {
        changes: [{
          uri: helperUri,
          type: 2,
          text: 'package demo.util; public class Helper { public static int twice(String value) { return value.length(); } }'
        }]
      }
    })));
    const deleted = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeWatchedFiles',
      params: {
        changes: [{ uri: helperUri, type: 3 }]
      }
    })));
    const messages = raw => (Array.isArray(raw) ? raw : [raw]).map(message => ({
      uri: message.params.uri,
      diagnostics: message.params.diagnostics.map(diagnostic => diagnostic.message)
    }));
    return {
      created: messages(created),
      opened: messages(opened),
      changed: messages(changed),
      deleted: messages(deleted)
    };
  });

  expect(result.created.find(message => message.uri.endsWith('/Helper.java')).diagnostics).toEqual([]);
  expect(result.opened.find(message => message.uri.endsWith('/App.java')).diagnostics)
    .toContain('The method twice(int) in the type Helper is not applicable for the arguments (String)');
  expect(result.changed.find(message => message.uri.endsWith('/App.java')).diagnostics)
    .not.toContain('The method twice(int) in the type Helper is not applicable for the arguments (String)');
  expect(result.deleted.find(message => message.uri.endsWith('/Helper.java')).diagnostics).toEqual([]);
  expect(result.deleted.find(message => message.uri.endsWith('/App.java')).diagnostics)
    .toContain('The import demo.util cannot be resolved');
});

test('LSP endpoint renames workspace sources and revalidates dependents', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const oldHelperUri = 'file:///workspace/src/demo/old/Helper.java';
    const newHelperUri = 'file:///workspace/src/demo/util/Helper.java';
    const appUri = 'file:///workspace/src/demo/App.java';
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/workspaceSources',
      params: {
        uri: oldHelperUri,
        text: 'package demo.old; public class Helper { public static int twice(String value) { return value.length(); } }'
      }
    }));
    const opened = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: appUri,
          text: 'package demo; import demo.old.Helper; public class App { int value() { return Helper.twice("ok"); } }'
        }
      }
    })));
    const renamed = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'java/browserJdtLs/renameWorkspaceSource',
      params: {
        oldUri: oldHelperUri,
        newUri: newHelperUri,
        text: 'package demo.util; public class Helper { public static int twice(String value) { return value.length(); } }'
      }
    })));
    const messages = raw => (Array.isArray(raw) ? raw : [raw]).map(message => ({
      uri: message.params.uri,
      diagnostics: message.params.diagnostics.map(diagnostic => diagnostic.message)
    }));
    return {
      opened: messages(opened),
      renamed: messages(renamed)
    };
  });

  const openedAppDiagnostics = result.opened.find(message => message.uri.endsWith('/App.java')).diagnostics;
  const renamedAppDiagnostics = result.renamed.find(message => message.uri.endsWith('/App.java')).diagnostics;
  const oldHelperDiagnostics = result.renamed.find(message => message.uri.endsWith('/old/Helper.java')).diagnostics;
  const newHelperDiagnostics = result.renamed.find(message => message.uri.endsWith('/util/Helper.java')).diagnostics;
  expect(openedAppDiagnostics).not.toContain('Helper cannot be resolved');
  expect(renamedAppDiagnostics).toContain('The import demo.old cannot be resolved');
  expect(renamedAppDiagnostics).toContain('Helper cannot be resolved');
  expect(oldHelperDiagnostics).toEqual([]);
  expect(newHelperDiagnostics).toEqual([]);
});

test('LSP endpoint republishes dependent diagnostics when an open source file changes', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const helperUri = 'file:///workspace/src/demo/Helper.java';
    const appUri = 'file:///workspace/src/demo/App.java';
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: helperUri,
          text: 'package demo; public class Helper { static int twice(int value) { return value * 2; } }'
        }
      }
    }));
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: appUri,
          text: 'package demo; public class App { int value() { return Helper.twice("bad"); } }'
        }
      }
    }));
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didChange',
      params: {
        textDocument: { uri: helperUri },
        contentChanges: [
          { text: 'package demo; public class Helper { static int twice(String value) { return value.length(); } }' }
        ]
      }
    })));
    const changed = Array.isArray(raw) ? raw : [raw];
    return changed.map(message => ({
      uri: message.params.uri,
      diagnostics: message.params.diagnostics.map(diagnostic => diagnostic.message)
    }));
  });

  const appDiagnostics = result.find(message => message.uri.endsWith('/App.java')).diagnostics;
  const helperDiagnostics = result.find(message => message.uri.endsWith('/Helper.java')).diagnostics;
  expect(appDiagnostics).not.toContain('The method twice(int) in the type Helper is not applicable for the arguments (String)');
  expect(appDiagnostics).not.toContain('Helper cannot be resolved to a variable');
  expect(helperDiagnostics).toEqual([]);
});

test('LSP endpoint reapplies diagnostics after compiler source configuration changes', async ({ page }) => {
  await page.goto('/index.html');
  const result = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    const uri = 'file:///workspace/src/demo/Point.java';
    const opened = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri,
          text: 'package demo; public record Point(int x, int y) {}'
        }
      }
    })));
    const configured = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeConfiguration',
      params: {
        settings: {
          java: {
            settings: {
              'org.eclipse.jdt.core.compiler.source': '11'
            }
          }
        }
      }
    })));
    const reset = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeConfiguration',
      params: {
        settings: {
          java: {
            settings: {
              'org.eclipse.jdt.core.compiler.source': '17'
            }
          }
        }
      }
    })));
    const messages = raw => (Array.isArray(raw) ? raw : [raw])
      .find(message => message.params.uri.endsWith('/Point.java')).params.diagnostics.map(diagnostic => diagnostic.message);
    return {
      opened: messages(opened),
      configured: messages(configured),
      reset: messages(reset)
    };
  });

  expect(result.opened).toEqual([]);
  expect(result.configured.length).toBeGreaterThan(0);
  expect(result.configured.some(message => message.includes('Syntax error') || message.includes('record') || message.includes('Record'))).toBe(true);
  expect(result.reset).toEqual([]);
});

test('ECJ diagnostics resolve common java.util imports in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/CollectionsUse.java', `
      package demo;
      import java.util.ArrayList;
      import java.util.List;

      public class CollectionsUse {
        int first() {
          List<String> names = new ArrayList<>();
          names.add("Ada");
          int value = names.get(0);
          return value;
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to int');
  expect(messages).not.toContain('java.util.ArrayList cannot be resolved to a type');
  expect(messages).not.toContain('java.util.List cannot be resolved to a type');
});

test('ECJ diagnostics resolve broader common JRE APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/CommonJreUse.java', `
      package demo;
      import java.util.Arrays;
      import java.util.Collections;
      import java.util.Comparator;
      import java.util.HashMap;
      import java.util.LinkedList;
      import java.util.List;
      import java.util.Map;
      import java.util.Objects;
      import java.util.Optional;
      import java.util.Queue;

      public class CommonJreUse {
        String render(String input) {
          List<String> names = Arrays.asList("Ada", "Grace");
          Collections.sort(names, Comparator.naturalOrder());
          Queue<String> queue = new LinkedList<>(names);
          Map<String, Integer> counts = new HashMap<>();
          counts.put(queue.peek(), Integer.valueOf(1));
          Optional<String> selected = Optional.ofNullable(queue.poll());
          StringBuilder builder = new StringBuilder(Objects.requireNonNull(input).trim());
          builder.append(selected.orElse("missing")).append(counts.get("Ada"));
          Integer wrong = selected.orElse("bad");
          return builder.toString();
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to Integer');
  for (const typeName of [
    'java.util.Arrays',
    'java.util.Collections',
    'java.util.Comparator',
    'java.util.HashMap',
    'java.util.LinkedList',
    'java.util.Map',
    'java.util.Objects',
    'java.util.Optional',
    'java.util.Queue',
    'StringBuilder',
    'Integer'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
});

test('ECJ diagnostics resolve collection factories and Optional chaining in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/CollectionFactoryUse.java', `
      package demo;
      import java.util.List;
      import java.util.Map;
      import java.util.Optional;
      import java.util.Set;

      public class CollectionFactoryUse {
        Integer first() {
          List<String> names = List.of("Ada", "Grace");
          Set<String> selected = Set.of(names.get(0), "Linus");
          Map<String, Integer> counts = Map.of("Ada", Integer.valueOf(1), "Grace", Integer.valueOf(2));
          Optional<String> first = Optional.ofNullable(names.get(0))
              .filter(value -> selected.contains(value))
              .map(value -> value.trim());
          first.ifPresent(value -> System.out.println(value));
          String wrong = counts.get(first.orElseGet(() -> "Ada"));
          return counts.get("Ada");
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from Integer to String');
  for (const typeName of [
    'java.util.List',
    'java.util.Set',
    'java.util.Map',
    'java.util.Optional'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The method of(String, String) is undefined');
  expect(messages).not.toContain('The method of(String, Integer, String, Integer) is undefined');
  expect(messages).not.toContain('The method filter');
  expect(messages).not.toContain('The method map');
  expect(messages).not.toContain('The method ifPresent');
  expect(messages).not.toContain('The method orElseGet');
});

test('ECJ diagnostics resolve stream collectors and common collection methods in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/CollectorUse.java', `
      package demo;
      import java.util.Comparator;
      import java.util.List;
      import java.util.Map;
      import java.util.function.Function;
      import java.util.function.Predicate;
      import java.util.stream.Collectors;

      public class CollectorUse {
        Integer summarize() {
          List<String> names = List.of("Ada", "Grace");
          names.addAll(List.of("Linus"));
          names.sort(Comparator.naturalOrder());
          Predicate<String> selected = value -> value.length() > 2;
          List<String> filtered = names.stream()
              .filter(selected.and(value -> value.contains("a")))
              .distinct()
              .sorted()
              .collect(Collectors.toList());
          Map<String, Integer> lengths = filtered.stream()
              .collect(Collectors.toMap(Function.identity(), String::length));
          String joined = filtered.stream().collect(Collectors.joining(","));
          for (Map.Entry<String, Integer> entry : lengths.entrySet()) {
            joined = joined + entry.getKey() + entry.getValue();
          }
          Integer wrong = joined;
          return lengths.get("Ada");
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to Integer');
  for (const typeName of [
    'java.util.stream.Collectors',
    'java.util.function.Function',
    'java.util.function.Predicate',
    'java.util.Comparator',
    'java.util.List',
    'java.util.Map'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The method addAll');
  expect(messages).not.toContain('The method sort');
  expect(messages).not.toContain('The method and');
  expect(messages).not.toContain('The method collect');
  expect(messages).not.toContain('The method toList');
  expect(messages).not.toContain('The method toMap');
  expect(messages).not.toContain('The method joining');
  expect(messages).not.toContain('Entry cannot be resolved to a type');
});

test('ECJ diagnostics resolve common concurrent APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/ConcurrentUse.java', `
      package demo;
      import java.util.concurrent.Callable;
      import java.util.concurrent.CompletableFuture;
      import java.util.concurrent.ExecutorService;
      import java.util.concurrent.Executors;
      import java.util.concurrent.Future;
      import java.util.concurrent.TimeUnit;

      public class ConcurrentUse {
        Integer run() throws Exception {
          ExecutorService executor = Executors.newSingleThreadExecutor();
          Callable<String> task = () -> "Ada";
          Future<String> future = executor.submit(task);
          CompletableFuture<Integer> length = CompletableFuture
              .supplyAsync(() -> "Grace", executor)
              .thenApply(value -> value.trim())
              .thenApply(String::length);
          String direct = future.get();
          TimeUnit.SECONDS.toMillis(1);
          String wrong = length.join();
          executor.shutdown();
          return length.get();
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from Integer to String');
  for (const typeName of [
    'java.util.concurrent.Callable',
    'java.util.concurrent.CompletableFuture',
    'java.util.concurrent.ExecutorService',
    'java.util.concurrent.Executors',
    'java.util.concurrent.Future',
    'java.util.concurrent.TimeUnit'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The method newSingleThreadExecutor() is undefined');
  expect(messages).not.toContain('The method supplyAsync');
  expect(messages).not.toContain('The method thenApply');
  expect(messages).not.toContain('The method get() is undefined');
});

test('ECJ diagnostics resolve common math, net, scanner, UUID, and formatter APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/UtilityApiUse.java', `
      package demo;
      import java.math.BigDecimal;
      import java.math.BigInteger;
      import java.net.URI;
      import java.net.URL;
      import java.time.LocalDate;
      import java.time.format.DateTimeFormatter;
      import java.util.Date;
      import java.util.Locale;
      import java.util.Scanner;
      import java.util.UUID;

      public class UtilityApiUse {
        String render(String raw) throws Exception {
          BigDecimal amount = new BigDecimal("19.99").add(BigDecimal.ONE);
          BigInteger count = BigInteger.valueOf(2).multiply(new BigInteger("3"));
          URI uri = URI.create("https://example.com/items/" + count.intValue());
          URL url = uri.toURL();
          Date date = new Date();
          Locale locale = Locale.forLanguageTag("en-US");
          Scanner scanner = new Scanner(raw).useLocale(locale);
          UUID id = UUID.randomUUID();
          String day = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());
          String token = scanner.hasNext() ? scanner.next() : id.toString();
          scanner.close();
          Integer wrong = amount;
          return url.getHost() + uri.getPath() + date.getTime() + locale.toLanguageTag() + day + token;
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from BigDecimal to Integer');
  for (const typeName of [
    'java.math.BigDecimal',
    'java.math.BigInteger',
    'java.net.URI',
    'java.net.URL',
    'java.time.format.DateTimeFormatter',
    'java.util.Date',
    'java.util.Locale',
    'java.util.Scanner',
    'java.util.UUID'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The method create(String) is undefined');
  expect(messages).not.toContain('The method toURL() is undefined');
  expect(messages).not.toContain('The method forLanguageTag(String) is undefined');
  expect(messages).not.toContain('The method randomUUID() is undefined');
  expect(messages).not.toContain('The method ofPattern');
  expect(messages).not.toContain('The method format');
});

test('ECJ diagnostics resolve common java.lang utility and wrapper APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/LangUtilityUse.java', `
      package demo;

      @FunctionalInterface
      interface Transformer {
        Double apply(Character value);
      }

      public class LangUtilityUse implements Cloneable {
        @SafeVarargs
        static <T> int count(T... values) {
          return values.length;
        }

        Double value(char input) {
          Character character = Character.valueOf(input);
          Byte small = Byte.valueOf((byte) 1);
          Short medium = Short.valueOf((short) 2);
          Float fraction = Float.valueOf(3.5f);
          Double amount = Double.valueOf(Math.sqrt(16.0));
          String rendered = String.valueOf(character) + String.format("%s", amount);
          Transformer transformer = value -> Double.valueOf(value.charValue());
          Integer wrong = amount;
          System.out.println(rendered + count(small, medium, fraction));
          return transformer.apply(character);
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from Double to Integer');
  for (const typeName of [
    'FunctionalInterface',
    'SafeVarargs',
    'Cloneable',
    'Character',
    'Byte',
    'Short',
    'Float',
    'Double',
    'Math',
    'String'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
});

test('ECJ diagnostics resolve common exception APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/ExceptionUse.java', `
      package demo;

      public class ExceptionUse {
        String read(String value) {
          try {
            if (value == null) {
              throw new NullPointerException("value");
            }
            if (value.isEmpty()) {
              throw new IllegalArgumentException("empty");
            }
            return value;
          } catch (RuntimeException error) {
            error.printStackTrace();
            String message = error.getMessage();
            Integer wrong = message;
            return message;
          }
        }

        void fail() {
          throw new UnsupportedOperationException("not implemented");
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to Integer');
  for (const typeName of [
    'Throwable',
    'Exception',
    'RuntimeException',
    'NullPointerException',
    'IllegalArgumentException',
    'UnsupportedOperationException'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The method getMessage() is undefined');
  expect(messages).not.toContain('The method printStackTrace() is undefined');
});

test('ECJ diagnostics resolve lambdas, method references, and stream APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/FunctionalUse.java', `
      package demo;
      import java.util.Arrays;
      import java.util.List;
      import java.util.Optional;
      import java.util.function.Function;
      import java.util.function.Predicate;
      import java.util.stream.Stream;

      public class FunctionalUse {
        Integer firstLength() {
          Function<String, Integer> length = String::length;
          Predicate<String> nonEmpty = value -> !value.isEmpty();
          Optional<Integer> first = Arrays.asList("Ada", "Grace")
              .stream()
              .filter(nonEmpty)
              .map(length)
              .findFirst();
          Runnable runnable = () -> System.out.println(first.orElse(0));
          runnable.run();
          Integer wrong = Stream.of("bad").map(value -> value.trim()).findFirst().orElse("bad");
          return first.orElse(0);
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to Integer');
  for (const typeName of [
    'java.util.function.Function',
    'java.util.function.Predicate',
    'java.util.stream.Stream',
    'Optional',
    'Runnable'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
  expect(messages).not.toContain('The target type of this expression must be a functional interface');
});

test('ECJ diagnostics resolve common IO, path, time, regex, and annotation APIs in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/PlatformApiUse.java', `
      package demo;
      import java.io.File;
      import java.io.IOException;
      import java.nio.file.Files;
      import java.nio.file.Path;
      import java.nio.file.Paths;
      import java.time.Duration;
      import java.time.Instant;
      import java.time.LocalDate;
      import java.util.regex.Matcher;
      import java.util.regex.Pattern;

      @Deprecated
      public class PlatformApiUse implements Runnable {
        @Override
        public void run() {
          Path path = Paths.get("src", "App.java").normalize();
          File file = new File(path.toString());
          Pattern pattern = Pattern.compile("App");
          Matcher matcher = pattern.matcher(file.getName());
          boolean matches = matcher.find() && Files.exists(path);
          LocalDate tomorrow = LocalDate.now().plusDays(1);
          Duration elapsed = Duration.between(Instant.parse("2024-01-01T00:00:00Z"), Instant.now());
          Integer wrong = tomorrow.toString();
          System.out.println(matches);
          System.out.println(elapsed.toSeconds());
        }

        void read(Path path) throws IOException {
          Files.readAllLines(path);
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from String to Integer');
  for (const typeName of [
    'java.io.File',
    'java.io.IOException',
    'java.nio.file.Files',
    'java.nio.file.Path',
    'java.nio.file.Paths',
    'java.time.Duration',
    'java.time.Instant',
    'java.time.LocalDate',
    'java.util.regex.Matcher',
    'java.util.regex.Pattern',
    'Deprecated',
    'Override'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
});

test('ECJ diagnostics resolve enums and custom annotations across folder files in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const response = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'workspace/didChangeWatchedFiles',
      params: {
        changes: [
          {
            uri: 'file:///workspace/src/demo/Mode.java',
            type: 1,
            text: 'package demo; public enum Mode { FAST, SLOW }'
          },
          {
            uri: 'file:///workspace/src/demo/Marker.java',
            type: 1,
            text: `
              package demo;
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;
              import java.lang.annotation.Target;

              @Retention(RetentionPolicy.RUNTIME)
              @Target(ElementType.TYPE)
              public @interface Marker {
                Mode value();
              }
            `
          }
        ]
      }
    }));
    const raw = JSON.parse(api.handle(JSON.stringify({
      jsonrpc: '2.0',
      method: 'textDocument/didOpen',
      params: {
        textDocument: {
          uri: 'file:///workspace/src/demo/App.java',
          text: 'package demo; @Marker(Mode.FAST) public class App { int bad() { return Mode.SLOW; } }'
        }
      }
    })));
    return Array.isArray(raw) ? raw.find(message => message.params.uri.endsWith('/App.java')) : raw;
  });

  const messages = response.params.diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('Type mismatch: cannot convert from Mode to int');
  for (const typeName of [
    'java.lang.Enum',
    'java.io.Serializable',
    'java.lang.annotation.Annotation',
    'java.lang.annotation.ElementType',
    'java.lang.annotation.Retention',
    'java.lang.annotation.RetentionPolicy',
    'java.lang.annotation.Target',
    'Mode',
    'Marker'
  ]) {
    expect(messages).not.toContain(`${typeName} cannot be resolved to a type`);
  }
});

test('ECJ warning-only diagnostics do not get fallback semantic errors in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/Warnings.java', `
      package demo;
      import java.util.ArrayList;
      import java.util.List;

      public class Warnings {
        private int hidden;

        void raw(List value) {
          List raw = new ArrayList<String>();
          List<String> names = raw;
          ;
          value = value;
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages).toContain('The value of the field Warnings.hidden is not used');
  expect(messages).toContain('List is a raw type. References to generic type List<E> should be parameterized');
  expect(messages).toContain('Type safety: The expression of type List needs unchecked conversion to conform to List<String>');
  expect(messages).toContain('The assignment to variable value has no effect');
  expect(messages).not.toContain('Type mismatch: cannot convert from List to List<String>');
  expect(diagnostics.every(diagnostic => diagnostic.severity === 2)).toBe(true);
});

test('ECJ errors with suppressed warnings do not get duplicate fallback type mismatches in Chromium', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/demo/Suppressed.java', `
      package demo;
      import java.util.ArrayList;
      import java.util.List;

      public class Suppressed {
        @SuppressWarnings({"rawtypes", "unchecked", "unused"})
        void raw() {
          List raw = new ArrayList<String>();
          List<String> names = raw;
          int broken = "bad";
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  expect(messages.filter(message => message === 'Type mismatch: cannot convert from String to int')).toHaveLength(1);
  expect(messages).not.toContain('Type mismatch: cannot convert from List to List<String>');
  expect(messages).not.toContain('List is a raw type. References to generic type List<E> should be parameterized');
  expect(messages).not.toContain('Type safety: The expression of type List needs unchecked conversion to conform to List<String>');
});

test('semantic diagnostics report common Java linting errors', async ({ page }) => {
  await page.goto('/index.html');
  const diagnostics = await page.evaluate(async () => {
    const api = await globalThis.browserJdtLsReady;
    return JSON.parse(api.lint('file:///workspace/src/App.java', `
      public class App {
        int field;
        int field;

        int badAssignment() {
          int number = "text";
          return "wrong";
        }

        void unresolved() {
          int other = missingValue;
        }

        int controlFlow(int used) {
          int unused = 1;
          if (1) {
            return 1;
          } else {
            return 2;
          }
          int dead = 3;
        }

        void duplicate(int value, int value) {
          int local = 1;
          int local = 2;
          local = local;
          missingCall();
          return 1;
        }
      }
    `));
  });

  const messages = diagnostics.map(diagnostic => diagnostic.message);
  const codes = diagnostics.map(diagnostic => diagnostic.code);
  expect(codes).toEqual(expect.arrayContaining([17, 19, 55, 56, 83, 100, 105, 178]));
  expect(messages).toContain('Type mismatch: cannot convert from String to int');
  expect(messages).toContain('missingValue cannot be resolved to a variable');
  expect(messages).toContain('The method missingCall() is undefined for the type App');
  expect(messages).toContain('Void methods cannot return a value');
  expect(messages).toContain('Duplicate field App.field');
  expect(messages).toContain('Type mismatch: cannot convert from int to boolean');
  expect(messages).toContain('Dead code');
  expect(messages).toContain('Duplicate local variable value');
  expect(messages).toContain('Duplicate local variable local');
  expect(messages).toContain('The assignment to variable has no effect');
  expect(messages).toContain('The value of the local variable unused is not used');
  expect(messages).toContain('Void methods cannot return a value');
  expect(diagnostics.filter(diagnostic => diagnostic.severity === 1).length).toBeGreaterThanOrEqual(9);
  expect(diagnostics.filter(diagnostic => diagnostic.severity === 2).length).toBeGreaterThanOrEqual(3);
});
