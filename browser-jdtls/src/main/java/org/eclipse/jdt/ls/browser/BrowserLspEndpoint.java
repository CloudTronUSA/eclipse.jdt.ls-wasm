package org.eclipse.jdt.ls.browser;

final class BrowserLspEndpoint {

	private BrowserLspEndpoint() {
	}

	static String handle(String payload, EcjDiagnosticsEngine engine) {
		String method = JsonSupport.stringField(payload, "method");
		if ("initialize".equals(method)) {
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload)
					+ ",\"result\":{\"capabilities\":{\"textDocumentSync\":{\"openClose\":true,\"change\":1}}}}";
		}
		if ("initialized".equals(method) || "exit".equals(method)) {
			return "";
		}
		if ("textDocument/didClose".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			return engine.close(uri);
		}
		if ("textDocument/didOpen".equals(method) || "textDocument/didChange".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			String text = JsonSupport.lastStringField(payload, "text");
			return engine.publishDiagnostics(uri, text);
		}
		if ("java/browserJdtLs/workspaceSources".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			String text = JsonSupport.lastStringField(payload, "text");
			return engine.updateWorkspaceSource(uri, text);
		}
		if ("java/browserJdtLs/removeWorkspaceSource".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			return engine.removeWorkspaceSource(uri);
		}
		if ("java/browserJdtLs/renameWorkspaceSource".equals(method)) {
			String oldUri = JsonSupport.stringField(payload, "oldUri");
			String newUri = JsonSupport.stringField(payload, "newUri");
			String text = JsonSupport.lastStringField(payload, "text");
			return engine.renameWorkspaceSource(oldUri, newUri, text);
		}
		if ("workspace/didChangeWatchedFiles".equals(method)) {
			return engine.changeWatchedFiles(JsonSupport.objectsInArrayField(payload, "changes"));
		}
		if ("workspace/didChangeConfiguration".equals(method)) {
			return engine.configure(payload);
		}
		if ("shutdown".equals(method)) {
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload) + ",\"result\":null}";
		}
		String id = JsonSupport.idField(payload);
		if ("null".equals(id)) {
			return "";
		}
		return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32601,\"message\":\"Unsupported method\"}}";
	}
}
