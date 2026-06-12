"use strict";

const DEFAULTS = {
	preferWasm: true,
	baseUrl: "./",
	wasmRuntimePath: "wasm/teavm/classes.wasm-runtime.js",
	wasmPath: "wasm/teavm/classes.wasm",
	jsPath: "js/teavm/classes.js"
};

let cached;

function load(options) {
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

function normalizeOptions(options) {
	const baseUrl = options?.baseUrl ?? DEFAULTS.baseUrl;
	return {
		preferWasm: options?.preferWasm !== false,
		wasmRuntimeUrl: options?.wasmRuntimeUrl ?? joinUrl(baseUrl, options?.wasmRuntimePath ?? DEFAULTS.wasmRuntimePath),
		wasmUrl: options?.wasmUrl ?? joinUrl(baseUrl, options?.wasmPath ?? DEFAULTS.wasmPath),
		jsUrl: options?.jsUrl ?? joinUrl(baseUrl, options?.jsPath ?? DEFAULTS.jsPath),
		wasmLoadOptions: options?.wasmLoadOptions
	};
}

function loadWasm(config) {
	if (!globalThis.WebAssembly) {
		return Promise.reject(new Error("WebAssembly is not available"));
	}
	return loadScript(config.wasmRuntimeUrl)
		.then(() => {
			const wasmGC = globalThis.TeaVM?.wasmGC;
			if (typeof wasmGC?.load !== "function") {
				throw new Error("TeaVM WebAssembly GC runtime did not initialize");
			}
			return wasmGC.load(config.wasmUrl, config.wasmLoadOptions);
		})
		.then(teavm => createApi("wasm", teavm.exports, teavm));
}

function loadJs(config) {
	return loadScript(config.jsUrl).then(() => createApi("js", globalThis, globalThis));
}

function createApi(target, exports, raw) {
	const missing = ["lint", "lintProcessing", "complete", "hover", "signatureHelp", "handle"].filter(name => typeof exports[name] !== "function");
	if (missing.length > 0) {
		throw new Error("JDT LS web " + target + " build is missing export(s): " + missing.join(", "));
	}
	const lint = exports.lint;
	const lintProcessing = exports.lintProcessing;
	const complete = exports.complete;
	const hover = exports.hover;
	const signatureHelp = exports.signatureHelp;
	const handle = exports.handle;
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

function handleFailureJson(payload, error) {
	let request;
	try {
		request = JSON.parse(payload);
	} catch (_error) {
		return "";
	}
	const method = request?.method;
	if (method === "textDocument/didOpen" || method === "textDocument/didChange" || method === "java/browserJdtLs/workspaceSources") {
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

function lastText(changes) {
	if (!Array.isArray(changes) || changes.length === 0) {
		return "";
	}
	const text = changes[changes.length - 1]?.text;
	return typeof text === "string" ? text : "";
}

function diagnosticsJson(source, prefix, error) {
	return JSON.stringify([diagnostic(source, prefix, error)]);
}

function mappedDiagnosticsJson(uri, source, prefix, error) {
	const data = diagnostic(source, prefix, error);
	return JSON.stringify([{ uri, ...data }]);
}

function publishDiagnosticsJson(uri, diagnostics) {
	return JSON.stringify([{
		jsonrpc: "2.0",
		method: "textDocument/publishDiagnostics",
		params: { uri, diagnostics }
	}]);
}

function diagnostic(source, prefix, error) {
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

function firstLineLength(source) {
	if (!source) {
		return 1;
	}
	const newline = source.indexOf("\n");
	return newline >= 0 ? newline : source.length;
}

function exceptionSummary(error) {
	if (error instanceof Error) {
		return error.name + (error.message ? ": " + error.message : "");
	}
	return String(error);
}

function loadScript(url) {
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

	if (globalThis.document?.createElement) {
		return new Promise((resolve, reject) => {
			const script = globalThis.document.createElement("script");
			script.async = true;
			script.src = url;
			script.onload = () => resolve();
			script.onerror = () => reject(new Error("Failed to load " + url));
			globalThis.document.head.appendChild(script);
		});
	}
	return Promise.reject(new Error("No browser script loader is available"));
}

function joinUrl(baseUrl, path) {
	if (/^[a-z][a-z0-9+.-]*:/i.test(path) || path.indexOf("//") === 0) {
		return path;
	}
	return String(baseUrl).replace(/\/?$/, "/") + path.replace(/^\//, "");
}

module.exports = { load };
