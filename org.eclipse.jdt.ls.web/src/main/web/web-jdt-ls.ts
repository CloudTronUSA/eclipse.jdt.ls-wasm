export interface WebJdtLsApi {
	target: "wasm" | "js";
	raw: unknown;
	fallbackError?: unknown;
	lint(uri: string, source: string): string;
	lintProcessing(entrypointUri: string, entrypointSource: string, additionalPdesJson: string): string;
	complete(uri: string, source: string, line: number, character: number): string;
	hover(uri: string, source: string, line: number, character: number): string;
	signatureHelp(uri: string, source: string, line: number, character: number): string;
	handle(payload: string): string;
}

export interface WebJdtLsLoadOptions {
	preferWasm?: boolean;
	baseUrl?: string;
	wasmRuntimePath?: string;
	wasmPath?: string;
	jsPath?: string;
	wasmRuntimeUrl?: string;
	wasmUrl?: string;
	jsUrl?: string;
	wasmLoadOptions?: unknown;
}

interface NormalizedOptions {
	preferWasm: boolean;
	wasmRuntimeUrl: string;
	wasmUrl: string;
	jsUrl: string;
	wasmLoadOptions?: unknown;
}

interface TeaVMWasmInstance {
	exports: Record<string, unknown>;
	instance: WebAssembly.Instance;
	module: WebAssembly.Module;
}

interface TeaVMGlobal {
	TeaVM?: {
		wasmGC?: {
			load(src: string, options?: unknown): Promise<TeaVMWasmInstance>;
		};
	};
	lint?: unknown;
	lintProcessing?: unknown;
	complete?: unknown;
	hover?: unknown;
	signatureHelp?: unknown;
	handle?: unknown;
	document?: Document;
}

declare const importScripts: ((...urls: string[]) => void) | undefined;

const DEFAULTS = {
	preferWasm: true,
	baseUrl: new URL("./", import.meta.url).href,
	wasmRuntimePath: "wasm/teavm/classes.wasm-runtime.js",
	wasmPath: "wasm/teavm/classes.wasm",
	jsPath: "js/teavm/classes.js"
};

let cached: Promise<WebJdtLsApi> | undefined;

export function load(options?: WebJdtLsLoadOptions): Promise<WebJdtLsApi> {
	if (cached && options == null) {
		return cached;
	}

	const config = normalizeOptions(options);
	const result = config.preferWasm
		? loadWasm(config).catch(error => loadJs(config).then(api => {
			api.fallbackError = error;
			return api;
		}))
		: loadJs(config);

	if (options == null) {
		cached = result;
	}
	return result;
}

function normalizeOptions(options?: WebJdtLsLoadOptions): NormalizedOptions {
	const baseUrl = options?.baseUrl ?? DEFAULTS.baseUrl;
	return {
		preferWasm: options?.preferWasm !== false,
		wasmRuntimeUrl: options?.wasmRuntimeUrl ?? joinUrl(baseUrl, options?.wasmRuntimePath ?? DEFAULTS.wasmRuntimePath),
		wasmUrl: options?.wasmUrl ?? joinUrl(baseUrl, options?.wasmPath ?? DEFAULTS.wasmPath),
		jsUrl: options?.jsUrl ?? joinUrl(baseUrl, options?.jsPath ?? DEFAULTS.jsPath),
		wasmLoadOptions: options?.wasmLoadOptions
	};
}

function loadWasm(config: NormalizedOptions): Promise<WebJdtLsApi> {
	const root = globalThis as TeaVMGlobal;
	if (!globalThis.WebAssembly) {
		return Promise.reject(new Error("WebAssembly is not available"));
	}
	return loadScript(config.wasmRuntimeUrl)
		.then(() => {
			const wasmGC = root.TeaVM?.wasmGC;
			if (typeof wasmGC?.load !== "function") {
				throw new Error("TeaVM WebAssembly GC runtime did not initialize");
			}
			return wasmGC.load(config.wasmUrl, config.wasmLoadOptions);
		})
		.then(teavm => createApi("wasm", teavm.exports, teavm));
}

function loadJs(config: NormalizedOptions): Promise<WebJdtLsApi> {
	return loadScript(config.jsUrl).then(() => createApi("js", globalThis as Record<string, unknown>, globalThis));
}

function createApi(target: "wasm" | "js", exports: Record<string, unknown>, raw: unknown): WebJdtLsApi {
	const missing = ["lint", "lintProcessing", "complete", "hover", "signatureHelp", "handle"]
		.filter(name => typeof exports[name] !== "function");
	if (missing.length > 0) {
		throw new Error("JDT LS web " + target + " build is missing export(s): " + missing.join(", "));
	}
	const lint = exports.lint as WebJdtLsApi["lint"];
	const lintProcessing = exports.lintProcessing as WebJdtLsApi["lintProcessing"];
	const complete = exports.complete as WebJdtLsApi["complete"];
	const hover = exports.hover as WebJdtLsApi["hover"];
	const signatureHelp = exports.signatureHelp as WebJdtLsApi["signatureHelp"];
	const handle = exports.handle as WebJdtLsApi["handle"];
	return {
		target,
		raw,
		lint: (uri, source) => {
			try {
				return lint(uri, source);
			} catch (error) {
				return diagnosticsJson(source, "Java analysis failed", error);
			}
		},
		lintProcessing: (entrypointUri, entrypointSource, additionalPdesJson) => {
			try {
				return lintProcessing(entrypointUri, entrypointSource, additionalPdesJson);
			} catch (error) {
				return mappedDiagnosticsJson(entrypointUri, entrypointSource, "Processing preprocessing failed", error);
			}
		},
		complete: (uri, source, line, character) => {
			try {
				return complete(uri, source, line, character);
			} catch (error) {
				return "[]";
			}
		},
		hover: (uri, source, line, character) => {
			try {
				return hover(uri, source, line, character);
			} catch (error) {
				return "null";
			}
		},
		signatureHelp: (uri, source, line, character) => {
			try {
				return signatureHelp(uri, source, line, character);
			} catch (error) {
				return "null";
			}
		},
		handle: payload => {
			try {
				return handle(payload);
			} catch (error) {
				return handleFailureJson(payload, error);
			}
		}
	};
}

function handleFailureJson(payload: string, error: unknown): string {
	let request: any;
	try {
		request = JSON.parse(payload);
	} catch (_error) {
		return "";
	}
	const method = request?.method;
	if (method === "textDocument/didOpen" || method === "textDocument/didChange"
			|| method === "java/browserJdtLs/workspaceSources") {
		const uri = request?.params?.textDocument?.uri ?? request?.params?.uri ?? "";
		const source = method === "textDocument/didChange"
			? lastText(request?.params?.contentChanges)
			: request?.params?.textDocument?.text ?? request?.params?.text ?? "";
		return publishDiagnosticsJson(uri, [diagnostic(source, "Java analysis failed", error)]);
	}
	if (method === "java/webJdtLs/processingSketch") {
		const uri = request?.params?.entrypointUri ?? "";
		const source = request?.params?.entrypointText ?? "";
		return publishDiagnosticsJson(uri, [diagnostic(source, "Processing preprocessing failed", error)]);
	}
	const id = request?.id;
	if (id === undefined || id === null) {
		return "";
	}
	return JSON.stringify({
		jsonrpc: "2.0",
		id,
		error: {
			code: -32603,
			message: "JDT LS web request failed: " + exceptionSummary(error)
		}
	});
}

function lastText(changes: unknown): string {
	if (!Array.isArray(changes) || changes.length === 0) {
		return "";
	}
	const text = changes[changes.length - 1]?.text;
	return typeof text === "string" ? text : "";
}

function diagnosticsJson(source: string, prefix: string, error: unknown): string {
	return JSON.stringify([diagnostic(source, prefix, error)]);
}

function mappedDiagnosticsJson(uri: string, source: string, prefix: string, error: unknown): string {
	const data = diagnostic(source, prefix, error);
	return JSON.stringify([{ uri, ...data }]);
}

function publishDiagnosticsJson(uri: string, diagnostics: unknown[]): string {
	return JSON.stringify([{
		jsonrpc: "2.0",
		method: "textDocument/publishDiagnostics",
		params: { uri, diagnostics }
	}]);
}

function diagnostic(source: string, prefix: string, error: unknown): unknown {
	const endCharacter = Math.max(1, firstLineLength(source));
	return {
		range: {
			start: { line: 0, character: 0 },
			end: { line: 0, character: endCharacter }
		},
		severity: 1,
		source: "Java",
		code: 0,
		message: prefix + ": " + exceptionSummary(error)
	};
}

function firstLineLength(source: string): number {
	if (!source) {
		return 1;
	}
	const newline = source.indexOf("\n");
	return newline >= 0 ? newline : source.length;
}

function exceptionSummary(error: unknown): string {
	if (error instanceof Error) {
		return error.name + (error.message ? ": " + error.message : "");
	}
	return String(error);
}

function loadScript(url: string): Promise<void> {
	if (typeof importScripts === "function") {
		return new Promise((resolve, reject) => {
			try {
				importScripts(url);
				resolve();
			} catch (error) {
				reject(error);
			}
		});
	}

	const root = globalThis as TeaVMGlobal;
	if (root.document?.createElement) {
		return new Promise((resolve, reject) => {
			const script = root.document!.createElement("script");
			script.async = true;
			script.src = url;
			script.onload = () => resolve();
			script.onerror = () => reject(new Error("Failed to load " + url));
			root.document!.head.appendChild(script);
		});
	}
	return Promise.reject(new Error("No browser script loader is available"));
}

function joinUrl(baseUrl: string, path: string): string {
	if (/^[a-z][a-z0-9+.-]*:/i.test(path) || path.indexOf("//") === 0) {
		return path;
	}
	return String(baseUrl).replace(/\/?$/, "/") + path.replace(/^\//, "");
}
