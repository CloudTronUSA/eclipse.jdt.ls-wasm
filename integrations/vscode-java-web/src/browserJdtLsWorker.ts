import './generated/browser-jdtls/classes.wasm-runtime.js';

declare const self: BrowserWorkerScope;

type BrowserWorkerScope = {
	location: { href: string };
	onmessage: ((event: { data: unknown }) => void) | null;
	postMessage(message: unknown): void;
};

type BrowserJdtLsApi = {
	handle(payload: string): string;
};

type TeaVMRuntime = {
	wasmGC: {
		load(path: string, options: unknown): Promise<{
			exports: {
				main(args: string[]): void;
				handle(payload: string): string;
			};
		}>;
	};
};

let backend: Promise<BrowserJdtLsApi> | undefined;
let extensionUri = "";
let messageQueue: Promise<void> = Promise.resolve();

self.onmessage = event => {
	messageQueue = messageQueue
		.then(() => handleMessage(event.data))
		.catch(error => {
			console.error(error);
		});
};

async function handleMessage(message: unknown): Promise<void> {
	console.log(`Browser JDT LS worker processing ${methodName(message) || messageKind(message)}`);
	if (isConfiguration(message)) {
		extensionUri = message.extensionUri;
		console.log('Browser JDT LS worker configured');
		return;
	}
	console.log(`Browser JDT LS worker received ${methodName(message)}`);
	const api = await getBackend();
	const rawResponse = api.handle(JSON.stringify(message));
	if (!rawResponse) {
		return;
	}
	const responses = JSON.parse(rawResponse) as unknown[];
	for (const response of Array.isArray(responses) ? responses : [responses]) {
		console.log(`Browser JDT LS worker sending ${methodName(response) || responseKind(response)}`);
		self.postMessage(response);
	}
}

async function getBackend(): Promise<BrowserJdtLsApi> {
	backend ??= loadBackend();
	return backend;
}

async function loadBackend(): Promise<BrowserJdtLsApi> {
	const wasmUrl = `${extensionUri}/resources/browser-jdtls/teavm/classes.wasm`;
	console.log(`Browser JDT LS worker loading ${wasmUrl}`);
	const teavmRuntime = (globalThis as unknown as Record<string, TeaVMRuntime>)['TeaVM'];
	const teavm = await teavmRuntime.wasmGC.load(wasmUrl, {
		stackDeobfuscator: { enabled: false }
	});
	teavm.exports.main([]);
	return {
		handle(payload: string): string {
			return teavm.exports.handle(payload);
		}
	};
}

function isConfiguration(message: unknown): message is { kind: string; extensionUri: string } {
	if (message === null || typeof message !== 'object') {
		return false;
	}
	const candidate = message as { kind?: unknown; extensionUri?: unknown };
	return candidate.kind === 'browser-jdtls/configure' && typeof candidate.extensionUri === 'string';
}

function methodName(message: unknown): string {
	if (message === null || typeof message !== 'object') {
		return '';
	}
	const candidate = message as { method?: unknown };
	return typeof candidate.method === 'string' ? candidate.method : '';
}

function messageKind(message: unknown): string {
	if (message === null || typeof message !== 'object') {
		return '';
	}
	const candidate = message as { kind?: unknown };
	return typeof candidate.kind === 'string' ? candidate.kind : '';
}

function responseKind(message: unknown): string {
	if (message === null || typeof message !== 'object') {
		return '';
	}
	const candidate = message as { id?: unknown; result?: unknown; error?: unknown };
	if (candidate.id !== undefined) {
		return candidate.error !== undefined ? `error:${String(candidate.id)}` : `response:${String(candidate.id)}`;
	}
	return '';
}
