import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions } from 'vscode-languageclient/browser';

type BrowserWorker = {
	postMessage(message: unknown): void;
	addEventListener(type: string, listener: (event: unknown) => void): void;
	onmessage: ((event: { data: unknown }) => void) | null;
	terminate(): void;
};
type BrowserWorkerConstructor = new(scriptURL: string | URL) => BrowserWorker;
type WatchedFileChange = {
	uri: string;
	type: 1 | 2 | 3;
	text?: string;
};

let client: LanguageClient | undefined;

const COMPILER_SETTING_KEYS = [
	'org.eclipse.jdt.core.compiler.source',
	'org.eclipse.jdt.core.compiler.compliance',
	'org.eclipse.jdt.core.compiler.codegen.targetPlatform'
];

export async function activate(context: vscode.ExtensionContext): Promise<void> {
	console.log('Browser JDT LS web extension activated');
	context.subscriptions.push(vscode.languages.onDidChangeDiagnostics(() => {
		const diagnostics = vscode.languages.getDiagnostics();
		const count = diagnostics.reduce((total, entry) => total + entry[1].length, 0);
		console.log(`Browser JDT LS VS Code diagnostics ${count}`);
		for (const [uri, entries] of diagnostics) {
			for (const diagnostic of entries) {
				console.log(`Browser JDT LS diagnostic ${uri.toString(true)} ${diagnostic.message}`);
			}
		}
		const snapshot = diagnostics.map(([uri, entries]) => ({
			uri: uri.toString(true),
			messages: entries.map(diagnostic => diagnostic.message)
		}));
		console.log(`Browser JDT LS diagnostic snapshot ${JSON.stringify(snapshot)}`);
	}));
	const workerUri = vscode.Uri.joinPath(context.extensionUri, 'dist/browserJdtLsWorker.js');
	const workerConstructor = (globalThis as unknown as Record<string, BrowserWorkerConstructor>)['Worker'];
	const worker = new workerConstructor(workerUri.toString(true));
	worker.postMessage({
		kind: 'browser-jdtls/configure',
		extensionUri: context.extensionUri.toString(true)
	});
	postCompilerConfiguration(worker);
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(event => {
		if (COMPILER_SETTING_KEYS.some(key => event.affectsConfiguration(`java.settings.${key}`))) {
			postCompilerConfiguration(worker);
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidSaveTextDocument(document => {
		if (document.languageId === 'java') {
			postWatchedFileChange(worker, document.uri, 2, document.getText());
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidDeleteFiles(event => {
		for (const uri of event.files) {
			if (uri.path.endsWith('.java')) {
				postWatchedFileDelete(worker, uri);
			}
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidCreateFiles(event => {
		for (const uri of event.files) {
			if (uri.path.endsWith('.java')) {
				void postWatchedFileCreateFromFs(worker, uri);
			}
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidRenameFiles(event => {
		for (const file of event.files) {
			void handleRename(worker, file.oldUri, file.newUri);
		}
	}));
	await preloadWorkspaceSources(worker);
	const clientOptions: LanguageClientOptions = {
		documentSelector: [
			{ language: 'java' }
		],
		synchronize: {},
		outputChannelName: 'Browser JDT LS'
	};
	client = new LanguageClient('java', 'Language Support for Java (Browser JDT LS)', clientOptions, worker);
	context.subscriptions.push(client);
	context.subscriptions.push(vscode.commands.registerCommand('java.browserJdtLs.status', async () => {
		await client?.start();
		return 'ready';
	}));
	context.subscriptions.push(vscode.commands.registerCommand('java.browserJdtLs.deleteWorkspaceFile', async (relativePath = 'demo/util/Helper.java') => {
		if (!vscode.workspace.workspaceFolders || vscode.workspace.workspaceFolders.length === 0) {
			return;
		}
		const uri = vscode.Uri.joinPath(vscode.workspace.workspaceFolders[0].uri, relativePath);
		await vscode.workspace.fs.delete(uri);
		postWatchedFileDelete(worker, uri);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('java.browserJdtLs.renameWorkspaceFile', async (
		fromRelativePath = 'demo/util/Helper.java',
		toRelativePath = 'demo/renamed/Helper.java'
	) => {
		if (!vscode.workspace.workspaceFolders || vscode.workspace.workspaceFolders.length === 0) {
			return;
		}
		const root = vscode.workspace.workspaceFolders[0].uri;
		const oldUri = vscode.Uri.joinPath(root, fromRelativePath);
		const newUri = vscode.Uri.joinPath(root, toRelativePath);
		await vscode.workspace.fs.createDirectory(vscode.Uri.joinPath(root, pathParent(toRelativePath)));
		await vscode.workspace.fs.rename(oldUri, newUri, { overwrite: true });
		await handleRename(worker, oldUri, newUri);
	}));
	context.subscriptions.push(vscode.commands.registerCommand('java.browserJdtLs.createWorkspaceFile', async (
		relativePath = 'demo/generated/Generated.java',
		text = 'package demo.generated;\n\npublic class Generated {\n\tpublic static int number() {\n\t\treturn 1;\n\t}\n}\n'
	) => {
		if (!vscode.workspace.workspaceFolders || vscode.workspace.workspaceFolders.length === 0) {
			return;
		}
		const root = vscode.workspace.workspaceFolders[0].uri;
		const uri = vscode.Uri.joinPath(root, relativePath);
		await vscode.workspace.fs.createDirectory(vscode.Uri.joinPath(root, pathParent(relativePath)));
		await vscode.workspace.fs.writeFile(uri, new TextEncoder().encode(text));
		postWatchedFileChange(worker, uri, 1, text);
	}));
	await client.start();
}

export async function deactivate(): Promise<void> {
	await client?.stop();
	client = undefined;
}

async function preloadWorkspaceSources(worker: BrowserWorker): Promise<void> {
	const javaFiles = await vscode.workspace.findFiles('**/*.java');
	const decoder = new TextDecoder();
	const changes: WatchedFileChange[] = [];
	for (const uri of javaFiles) {
		const bytes = await vscode.workspace.fs.readFile(uri);
		changes.push({
			uri: uri.toString(true),
			type: 1,
			text: decoder.decode(bytes)
		});
	}
	postWatchedFileChanges(worker, changes);
	console.log(`Browser JDT LS preloaded workspace Java sources ${javaFiles.length}`);
}

async function handleRename(worker: BrowserWorker, oldUri: vscode.Uri, newUri: vscode.Uri): Promise<void> {
	const oldJava = oldUri.path.endsWith('.java');
	const newJava = newUri.path.endsWith('.java');
	if (oldJava && !newJava) {
		postWatchedFileDelete(worker, oldUri);
		return;
	}
	if (!newJava) {
		return;
	}
	const bytes = await vscode.workspace.fs.readFile(newUri);
	const text = new TextDecoder().decode(bytes);
	if (oldJava) {
		postWatchedFileRename(worker, oldUri, newUri, text);
	} else {
		postWatchedFileChange(worker, newUri, 1, text);
	}
}

async function postWatchedFileCreateFromFs(worker: BrowserWorker, uri: vscode.Uri): Promise<void> {
	const bytes = await vscode.workspace.fs.readFile(uri);
	postWatchedFileChange(worker, uri, 1, new TextDecoder().decode(bytes));
}

function postWatchedFileChange(worker: BrowserWorker, uri: vscode.Uri, type: 1 | 2, text: string): void {
	postWatchedFileChanges(worker, [{
		uri: uri.toString(true),
		type,
		text
	}]);
}

function postWatchedFileDelete(worker: BrowserWorker, uri: vscode.Uri): void {
	postWatchedFileChanges(worker, [{
		uri: uri.toString(true),
		type: 3
	}]);
}

function postWatchedFileRename(worker: BrowserWorker, oldUri: vscode.Uri, newUri: vscode.Uri, text: string): void {
	postWatchedFileChanges(worker, [
		{
			uri: oldUri.toString(true),
			type: 3
		},
		{
			uri: newUri.toString(true),
			type: 1,
			text
		}
	]);
}

function postWatchedFileChanges(worker: BrowserWorker, changes: WatchedFileChange[]): void {
	if (changes.length === 0) {
		return;
	}
	worker.postMessage({
		jsonrpc: '2.0',
		method: 'workspace/didChangeWatchedFiles',
		params: {
			changes
		}
	});
}

function postCompilerConfiguration(worker: BrowserWorker): void {
	const javaSettings = vscode.workspace.getConfiguration('java').get<Record<string, string>>('settings', {});
	const settings: Record<string, string> = {};
	for (const key of COMPILER_SETTING_KEYS) {
		const value = javaSettings[key];
		if (typeof value === 'string' && value.length > 0) {
			settings[key] = value;
		}
	}
	worker.postMessage({
		jsonrpc: '2.0',
		method: 'workspace/didChangeConfiguration',
		params: {
			settings: {
				java: {
					settings
				}
			}
		}
	});
}

function pathParent(relativePath: string): string {
	const index = relativePath.lastIndexOf('/');
	return index >= 0 ? relativePath.substring(0, index) : '.';
}
