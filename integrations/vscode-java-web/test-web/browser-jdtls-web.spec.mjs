import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runServer } from '../node_modules/@vscode/test-web/out/server/main.js';
import { downloadAndUnzipVSCode } from '../node_modules/@vscode/test-web/out/server/download.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const extensionRoot = path.resolve(__dirname, '..');
const workspaceRoot = path.resolve(extensionRoot, 'test-web/workspace');
const vscodePort = 3100;

let server;

test.beforeAll(async () => {
	const build = await downloadAndUnzipVSCode(path.resolve(extensionRoot, '.vscode-test-web'), 'insider');
	server = await runServer('localhost', vscodePort, {
		extensionDevelopmentPath: extensionRoot,
		build,
		folderMountPath: workspaceRoot,
		printServerLog: false
	});
});

test.beforeEach(async () => {
	await resetWorkspaceFixture();
});

test.afterAll(async () => {
	server?.close();
});

test('vscode-java web extension reports TeaVM-backed Java diagnostics', async ({ page }) => {
	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['demo'], /App\.java/);
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS preloaded workspace Java sources 3')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => firstLogIndex(logs, 'Browser JDT LS worker processing workspace/didChangeWatchedFiles') >= 0
		&& firstLogIndex(logs, 'Browser JDT LS worker processing workspace/didChangeWatchedFiles') < firstLogIndex(logs, 'Browser JDT LS worker processing initialize'), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);

	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS worker sending textDocument/publishDiagnostics')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS VS Code diagnostics 12')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await expect.poll(() => logs.some(line => line.includes('Helper.java The value of the local variable unused is not used')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await expect.poll(() => logs.some(line => line.includes('App.java Type mismatch: cannot convert from String to int')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await expect.poll(() => logs.some(line => line.includes('App.java The method twice(int) in the type Helper is not applicable for the arguments (String)')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await expect.poll(() => logs.some(line => line.includes('App.java Type mismatch: cannot convert from String to Integer')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	await openWorkspaceFile(page, ['demo', 'util'], /Helper\.java/);
	await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A');
	await page.keyboard.type(`package demo.util;

public class Helper {
\tpublic static int twice(String value) {
\t\treturn value.length();
\t}
}
`);
	await page.keyboard.press(process.platform === 'darwin' ? 'Meta+S' : 'Control+S');
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& !entry.messages.some(message => message.includes('The method twice(int) in the type Helper is not applicable for the arguments (String)'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await page.keyboard.press(process.platform === 'darwin' ? 'Meta+W' : 'Control+W');
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/Helper.java')
			&& !entry.messages.some(message => message.includes('The value of the local variable unused is not used'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await runCommand(page, 'Browser JDT LS: Delete Test Workspace File');
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& entry.messages.some(message => message.includes('The import demo.util cannot be resolved'))
			&& entry.messages.some(message => message.includes('Helper cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util.Optional cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('StringBuilder cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('InternalHelper cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('InternalValue cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util.function.Function cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util.function.Predicate cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util.stream.Stream cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('Stream cannot be resolved'))).toEqual([]);
	expect(logs.filter(line => line.includes('The target type of this expression must be a functional interface'))).toEqual([]);
});

test('vscode-java web extension revalidates Java diagnostics after workspace file rename', async ({ page }) => {
	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['demo'], /App\.java/);
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS preloaded workspace Java sources 3')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& entry.messages.some(message => message.includes('The method twice(int) in the type Helper is not applicable for the arguments (String)'))
			&& !entry.messages.some(message => message.includes('The import demo.util cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Helper cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	await runCommand(page, 'Browser JDT LS: Rename Test Workspace File');
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS worker processing workspace/didChangeWatchedFiles')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/demo/renamed/Helper.java')
			&& entry.messages.some(message => message.includes('The value of the local variable unused is not used'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& entry.messages.some(message => message.includes('The method twice(int) in the type Helper is not applicable for the arguments (String)'))
			&& !entry.messages.some(message => message.includes('The import demo.util cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Helper cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
	expect(logs.filter(line => line.includes('java.util cannot be resolved'))).toEqual([]);
});

test('vscode-java web extension revalidates Java diagnostics after workspace file create', async ({ page }) => {
	await fs.writeFile(path.join(workspaceRoot, 'demo/App.java'), `package demo;

import demo.generated.Generated;

public class App {
\tint value() {
\t\treturn Generated.number();
\t}
}
`);
	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['demo'], /App\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& entry.messages.some(message => message.includes('The import demo.generated cannot be resolved'))
			&& entry.messages.some(message => message.includes('Generated cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	await runCommand(page, 'Browser JDT LS: Create Test Workspace File');
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS worker processing workspace/didChangeWatchedFiles')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/App.java')
			&& !entry.messages.some(message => message.includes('The import demo.generated cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Generated cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension applies Java compiler source settings in the browser worker', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, '.vscode'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, '.vscode/settings.json'), JSON.stringify({
		'java.settings': {
			'org.eclipse.jdt.core.compiler.source': '11'
		}
	}, null, 2));
	await fs.writeFile(path.join(workspaceRoot, 'demo/Point.java'), 'package demo;\n\npublic record Point(int x, int y) {}\n');

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['demo'], /Point\.java/);
	await expect.poll(() => logs.some(line => line.includes('Browser JDT LS worker processing workspace/didChangeConfiguration')), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/Point.java')
			&& entry.messages.some(message => message.includes('Syntax error') || message.includes('record') || message.includes('Record'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension reports package mismatches from Java source roots', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/com/example'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/com/example/App.java'), 'package wrong.place;\n\npublic class App {}\n');

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'com', 'example'], /App\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/com/example/App.java')
			&& entry.messages.some(message => message.includes('declared package')
				&& message.includes('wrong.place')
				&& message.includes('com.example'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves enums and annotations across folder files', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/Mode.java'), 'package demo;\n\npublic enum Mode { FAST, SLOW }\n');
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/Marker.java'), `package demo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Marker {
\tMode value();
}
`);
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/App.java'), 'package demo;\n\n@Marker(Mode.FAST)\npublic class App { int bad() { return Mode.SLOW; } }\n');

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /App\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/App.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from Mode to int'))
			&& !entry.messages.some(message => message.includes('java.lang.Enum cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.lang.annotation.Annotation cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Marker cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Mode cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves common java.lang utility APIs', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/LangUtilityUse.java'), `package demo;

@FunctionalInterface
interface Transformer {
\tDouble apply(Character value);
}

public class LangUtilityUse implements Cloneable {
\t@SafeVarargs
\tstatic <T> int count(T... values) {
\t\treturn values.length;
\t}

\tDouble value(char input) {
\t\tCharacter character = Character.valueOf(input);
\t\tByte small = Byte.valueOf((byte) 1);
\t\tShort medium = Short.valueOf((short) 2);
\t\tFloat fraction = Float.valueOf(3.5f);
\t\tDouble amount = Double.valueOf(Math.sqrt(16.0));
\t\tString rendered = String.valueOf(character) + String.format("%s", amount);
\t\tTransformer transformer = value -> Double.valueOf(value.charValue());
\t\tInteger wrong = amount;
\t\tSystem.out.println(rendered + count(small, medium, fraction));
\t\treturn transformer.apply(character);
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /LangUtilityUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/LangUtilityUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from Double to Integer'))
			&& !entry.messages.some(message => message.includes('FunctionalInterface cannot be resolved'))
			&& !entry.messages.some(message => message.includes('SafeVarargs cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Cloneable cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Character cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Double cannot be resolved'))
			&& !entry.messages.some(message => message.includes('Math cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves collection factories and Optional chaining', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/CollectionFactoryUse.java'), `package demo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CollectionFactoryUse {
\tInteger first() {
\t\tList<String> names = List.of("Ada", "Grace");
\t\tSet<String> selected = Set.of(names.get(0), "Linus");
\t\tMap<String, Integer> counts = Map.of("Ada", Integer.valueOf(1), "Grace", Integer.valueOf(2));
\t\tOptional<String> first = Optional.ofNullable(names.get(0))
\t\t\t\t.filter(value -> selected.contains(value))
\t\t\t\t.map(value -> value.trim());
\t\tfirst.ifPresent(value -> System.out.println(value));
\t\tString wrong = counts.get(first.orElseGet(() -> "Ada"));
\t\treturn counts.get("Ada");
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /CollectionFactoryUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/CollectionFactoryUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from Integer to String'))
			&& !entry.messages.some(message => message.includes('java.util.List cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Set cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Map cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Optional cannot be resolved'))
			&& !entry.messages.some(message => message.includes('The method of'))
			&& !entry.messages.some(message => message.includes('The method filter'))
			&& !entry.messages.some(message => message.includes('The method map'))
			&& !entry.messages.some(message => message.includes('The method ifPresent'))
			&& !entry.messages.some(message => message.includes('The method orElseGet'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves stream collectors and common collection methods', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/CollectorUse.java'), `package demo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CollectorUse {
\tInteger summarize() {
\t\tList<String> names = List.of("Ada", "Grace");
\t\tnames.addAll(List.of("Linus"));
\t\tnames.sort(Comparator.naturalOrder());
\t\tPredicate<String> selected = value -> value.length() > 2;
\t\tList<String> filtered = names.stream()
\t\t\t\t.filter(selected.and(value -> value.contains("a")))
\t\t\t\t.distinct()
\t\t\t\t.sorted()
\t\t\t\t.collect(Collectors.toList());
\t\tMap<String, Integer> lengths = filtered.stream()
\t\t\t\t.collect(Collectors.toMap(Function.identity(), String::length));
\t\tString joined = filtered.stream().collect(Collectors.joining(","));
\t\tfor (Map.Entry<String, Integer> entry : lengths.entrySet()) {
\t\t\tjoined = joined + entry.getKey() + entry.getValue();
\t\t}
\t\tInteger wrong = joined;
\t\treturn lengths.get("Ada");
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /CollectorUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/CollectorUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from String to Integer'))
			&& !entry.messages.some(message => message.includes('java.util.stream.Collectors cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.function.Function cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.function.Predicate cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Comparator cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.List cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Map cannot be resolved'))
			&& !entry.messages.some(message => message.includes('The method addAll'))
			&& !entry.messages.some(message => message.includes('The method sort'))
			&& !entry.messages.some(message => message.includes('The method and'))
			&& !entry.messages.some(message => message.includes('The method collect'))
			&& !entry.messages.some(message => message.includes('The method toList'))
			&& !entry.messages.some(message => message.includes('The method toMap'))
			&& !entry.messages.some(message => message.includes('The method joining'))
			&& !entry.messages.some(message => message.includes('Entry cannot be resolved'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves common concurrent APIs', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/ConcurrentUse.java'), `package demo;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentUse {
\tInteger run() throws Exception {
\t\tExecutorService executor = Executors.newSingleThreadExecutor();
\t\tCallable<String> task = () -> "Ada";
\t\tFuture<String> future = executor.submit(task);
\t\tCompletableFuture<Integer> length = CompletableFuture
\t\t\t\t.supplyAsync(() -> "Grace", executor)
\t\t\t\t.thenApply(value -> value.trim())
\t\t\t\t.thenApply(String::length);
\t\tString direct = future.get();
\t\tTimeUnit.SECONDS.toMillis(1);
\t\tString wrong = length.join();
\t\texecutor.shutdown();
\t\treturn length.get();
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /ConcurrentUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/ConcurrentUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from Integer to String'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.Callable cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.CompletableFuture cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.ExecutorService cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.Executors cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.Future cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.concurrent.TimeUnit cannot be resolved'))
			&& !entry.messages.some(message => message.includes('The method newSingleThreadExecutor() is undefined'))
			&& !entry.messages.some(message => message.includes('The method supplyAsync'))
			&& !entry.messages.some(message => message.includes('The method thenApply'))
			&& !entry.messages.some(message => message.includes('The method get() is undefined'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves common math, net, scanner, UUID, and formatter APIs', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/UtilityApiUse.java'), `package demo;

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
\tString render(String raw) throws Exception {
\t\tBigDecimal amount = new BigDecimal("19.99").add(BigDecimal.ONE);
\t\tBigInteger count = BigInteger.valueOf(2).multiply(new BigInteger("3"));
\t\tURI uri = URI.create("https://example.com/items/" + count.intValue());
\t\tURL url = uri.toURL();
\t\tDate date = new Date();
\t\tLocale locale = Locale.forLanguageTag("en-US");
\t\tScanner scanner = new Scanner(raw).useLocale(locale);
\t\tUUID id = UUID.randomUUID();
\t\tString day = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());
\t\tString token = scanner.hasNext() ? scanner.next() : id.toString();
\t\tscanner.close();
\t\tInteger wrong = amount;
\t\treturn url.getHost() + uri.getPath() + date.getTime() + locale.toLanguageTag() + day + token;
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /UtilityApiUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/UtilityApiUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from BigDecimal to Integer'))
			&& !entry.messages.some(message => message.includes('java.math.BigDecimal cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.math.BigInteger cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.net.URI cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.net.URL cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.time.format.DateTimeFormatter cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Date cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Locale cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.Scanner cannot be resolved'))
			&& !entry.messages.some(message => message.includes('java.util.UUID cannot be resolved'))
			&& !entry.messages.some(message => message.includes('The method create(String) is undefined'))
			&& !entry.messages.some(message => message.includes('The method toURL() is undefined'))
			&& !entry.messages.some(message => message.includes('The method forLanguageTag(String) is undefined'))
			&& !entry.messages.some(message => message.includes('The method randomUUID() is undefined'))
			&& !entry.messages.some(message => message.includes('The method format'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

test('vscode-java web extension resolves common exception APIs', async ({ page }) => {
	await fs.mkdir(path.join(workspaceRoot, 'src/main/java/demo'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'src/main/java/demo/ExceptionUse.java'), `package demo;

public class ExceptionUse {
\tString read(String value) {
\t\ttry {
\t\t\tif (value == null) {
\t\t\t\tthrow new NullPointerException("value");
\t\t\t}
\t\t\tif (value.isEmpty()) {
\t\t\t\tthrow new IllegalArgumentException("empty");
\t\t\t}
\t\t\treturn value;
\t\t} catch (RuntimeException error) {
\t\t\terror.printStackTrace();
\t\t\tString message = error.getMessage();
\t\t\tInteger wrong = message;
\t\t\treturn message;
\t\t}
\t}

\tvoid fail() {
\t\tthrow new UnsupportedOperationException("not implemented");
\t}
}
`);

	const logs = [];
	const pageErrors = [];
	page.on('console', message => logs.push(message.text()));
	page.on('pageerror', error => pageErrors.push(error.stack || error.message));
	await page.goto(`http://localhost:${vscodePort}/`);
	await page.waitForSelector('.monaco-workbench');
	await openWorkspaceFile(page, ['src', 'main', 'java', 'demo'], /ExceptionUse\.java/);
	await expect.poll(() => latestDiagnosticSnapshot(logs)
		.some(entry => entry.uri.endsWith('/src/main/java/demo/ExceptionUse.java')
			&& entry.messages.some(message => message.includes('Type mismatch: cannot convert from String to Integer'))
			&& !entry.messages.some(message => message.includes('RuntimeException cannot be resolved'))
			&& !entry.messages.some(message => message.includes('NullPointerException cannot be resolved'))
			&& !entry.messages.some(message => message.includes('UnsupportedOperationException cannot be resolved'))
			&& !entry.messages.some(message => message.includes('getMessage() is undefined'))
			&& !entry.messages.some(message => message.includes('printStackTrace() is undefined'))), {
		timeout: 30_000,
		message: `Console:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`
	}).toBe(true).catch(error => {
		throw new Error(`${error.message}\nConsole:\n${logs.join('\n')}\nPage errors:\n${pageErrors.join('\n')}`);
	});

	expect(logs.filter(line => line.includes('Failed to load'))).toEqual([]);
});

async function openWorkspaceFile(page, folders, fileName) {
	for (const folder of folders) {
		const item = page.getByRole('treeitem', { name: new RegExp(folder) }).first();
		await item.click();
		await page.keyboard.press('ArrowRight');
	}
	await page.getByRole('treeitem', { name: fileName }).dblclick();
	await expect(page.locator('.monaco-editor')).toBeVisible();
}

async function runCommand(page, label) {
	await page.keyboard.press(process.platform === 'darwin' ? 'Meta+Shift+P' : 'Control+Shift+P');
	await page.keyboard.type(label);
	await page.keyboard.press('Enter');
}

function latestDiagnosticSnapshot(logs) {
	const prefix = 'Browser JDT LS diagnostic snapshot ';
	for (let i = logs.length - 1; i >= 0; i--) {
		const line = logs[i];
		const index = line.indexOf(prefix);
		if (index >= 0) {
			const json = line.substring(index + prefix.length);
			const end = json.lastIndexOf('}]');
			return JSON.parse(end >= 0 ? json.substring(0, end + 2) : json);
		}
	}
	return [];
}

function firstLogIndex(logs, text) {
	return logs.findIndex(line => line.includes(text));
}

async function resetWorkspaceFixture() {
	await fs.rm(path.join(workspaceRoot, '.vscode'), { recursive: true, force: true });
	await fs.rm(path.join(workspaceRoot, 'demo'), { recursive: true, force: true });
	await fs.rm(path.join(workspaceRoot, 'src'), { recursive: true, force: true });
	await fs.mkdir(path.join(workspaceRoot, 'demo/util'), { recursive: true });
	await fs.writeFile(path.join(workspaceRoot, 'demo/App.java'), `package demo;

import demo.util.Helper;
import java.util.ArrayList;
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
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class App {
\tint badAssignment() {
\t\tint number = "text";
\t\treturn "wrong";
\t}

\tint crossFileCall() {
\t\treturn Helper.twice("bad");
\t}

\tint collectionTypeCheck() {
\t\tList<String> names = new ArrayList<>();
\t\tnames.add("Ada");
\t\tint value = names.get(0);
\t\treturn value;
\t}

\tString commonJreApis(String input) {
\t\tList<String> names = Arrays.asList("Ada", "Grace");
\t\tCollections.sort(names, Comparator.naturalOrder());
\t\tQueue<String> queue = new LinkedList<>(names);
\t\tMap<String, Integer> counts = new HashMap<>();
\t\tcounts.put(queue.peek(), Integer.valueOf(1));
\t\tOptional<String> selected = Optional.ofNullable(queue.poll());
\t\tStringBuilder builder = new StringBuilder(Objects.requireNonNull(input).trim());
\t\tbuilder.append(selected.orElse("missing")).append(counts.get("Ada"));
\t\tInteger wrong = selected.orElse("bad");
\t\treturn builder.toString() + InternalHelper.value(new InternalValue());
\t}

\tInteger functionalApis() {
\t\tFunction<String, Integer> length = String::length;
\t\tPredicate<String> nonEmpty = value -> !value.isEmpty();
\t\tOptional<Integer> first = Arrays.asList("Ada", "Grace")
\t\t\t\t.stream()
\t\t\t\t.filter(nonEmpty)
\t\t\t\t.map(length)
\t\t\t\t.findFirst();
\t\tRunnable runnable = () -> System.out.println(first.orElse(0));
\t\trunnable.run();
\t\tInteger wrong = Stream.of("bad").map(value -> value.trim()).findFirst().orElse("bad");
\t\treturn first.orElse(0);
\t}

\tvoid unresolved() {
\t\tint other = missingValue;
\t}
}
`);
	await fs.writeFile(path.join(workspaceRoot, 'demo/Types.java'), `package demo;

class InternalHelper {
\tstatic int value(InternalValue value) {
\t\treturn value.number();
\t}
}

class InternalValue {
\tint number() {
\t\treturn 1;
\t}
}
`);
	await fs.writeFile(path.join(workspaceRoot, 'demo/util/Helper.java'), `package demo.util;

public class Helper {
\tpublic static int twice(int value) {
\t\tint unused = 0;
\t\treturn value * 2;
\t}
}
`);
}
