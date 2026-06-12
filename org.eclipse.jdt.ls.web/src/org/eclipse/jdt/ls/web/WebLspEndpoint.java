package org.eclipse.jdt.ls.web;

final class WebLspEndpoint {

	private WebLspEndpoint() {
	}

	static String handle(String payload, EcjDiagnosticsEngine engine) {
		try {
			return handleUnsafe(payload, engine);
		} catch (Throwable ex) {
			return failureResponse(payload, ex);
		}
	}

	private static String handleUnsafe(String payload, EcjDiagnosticsEngine engine) {
		String method = JsonSupport.stringField(payload, "method");
		if ("initialize".equals(method)) {
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload)
					+ ",\"result\":{\"capabilities\":{\"textDocumentSync\":{\"openClose\":true,\"change\":1},"
					+ "\"completionProvider\":{\"resolveProvider\":false,\"triggerCharacters\":[\".\",\"@\"]},"
					+ "\"hoverProvider\":true,"
					+ "\"signatureHelpProvider\":{\"triggerCharacters\":[\"(\",\",\"],\"retriggerCharacters\":[\",\"]}}}}";
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
		if ("textDocument/completion".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			int line = JsonSupport.intField(payload, "line");
			int character = JsonSupport.intField(payload, "character");
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload) + ",\"result\":"
					+ engine.completion(uri, line, character) + "}";
		}
		if ("textDocument/hover".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			int line = JsonSupport.intField(payload, "line");
			int character = JsonSupport.intField(payload, "character");
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload) + ",\"result\":"
					+ engine.hover(uri, line, character) + "}";
		}
		if ("textDocument/signatureHelp".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			int line = JsonSupport.intField(payload, "line");
			int character = JsonSupport.intField(payload, "character");
			return "{\"jsonrpc\":\"2.0\",\"id\":" + JsonSupport.idField(payload) + ",\"result\":"
					+ engine.signatureHelp(uri, line, character) + "}";
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
		if ("java/webJdtLs/processingSketch".equals(method)) {
			String uri = JsonSupport.stringField(payload, "entrypointUri");
			String text = JsonSupport.stringField(payload, "entrypointText");
			return engine.publishProcessingDiagnostics(uri, text, JsonSupport.objectsInArrayField(payload, "sources"));
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

	private static String failureResponse(String payload, Throwable ex) {
		String method = JsonSupport.stringField(payload, "method");
		if ("textDocument/didOpen".equals(method) || "textDocument/didChange".equals(method)
				|| "java/browserJdtLs/workspaceSources".equals(method)) {
			String uri = JsonSupport.stringField(payload, "uri");
			String text = JsonSupport.lastStringField(payload, "text");
			return publishFailureDiagnostic(uri, text, ex);
		}
		if ("java/webJdtLs/processingSketch".equals(method)) {
			String uri = JsonSupport.stringField(payload, "entrypointUri");
			String text = JsonSupport.stringField(payload, "entrypointText");
			return publishFailureDiagnostic(uri, text, ex);
		}
		String id = JsonSupport.idField(payload);
		if ("null".equals(id)) {
			return "";
		}
		StringBuilder json = new StringBuilder();
		json.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id)
				.append(",\"error\":{\"code\":-32603,\"message\":");
		JsonSupport.appendString(json, "JDT LS web request failed: " + exceptionSummary(ex));
		json.append("}}");
		return json.toString();
	}

	private static String publishFailureDiagnostic(String uri, String source, Throwable ex) {
		StringBuilder json = new StringBuilder();
		json.append("[{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/publishDiagnostics\",\"params\":{\"uri\":");
		JsonSupport.appendString(json, uri);
		json.append(",\"diagnostics\":[");
		appendFailureDiagnostic(json, source, ex);
		json.append("]}}]");
		return json.toString();
	}

	private static void appendFailureDiagnostic(StringBuilder json, String source, Throwable ex) {
		int endCharacter = 1;
		if (source != null && !source.isEmpty()) {
			int newline = source.indexOf('\n');
			endCharacter = Math.max(1, newline >= 0 ? newline : source.length());
		}
		json.append("{\"range\":{\"start\":{\"line\":0,\"character\":0},\"end\":{\"line\":0,\"character\":")
				.append(endCharacter)
				.append("}},\"severity\":1,\"source\":\"Java\",\"code\":0,\"message\":");
		JsonSupport.appendString(json, "Java analysis failed: " + exceptionSummary(ex));
		json.append('}');
	}

	private static String exceptionSummary(Throwable ex) {
		StringBuilder summary = new StringBuilder();
		appendException(summary, ex);
		Throwable cause = ex.getCause();
		int depth = 0;
		while (cause != null && cause != ex && depth++ < 3) {
			summary.append("; caused by ");
			appendException(summary, cause);
			cause = cause.getCause();
		}
		String compilerFrame = compilerFrame(ex);
		if (!compilerFrame.isEmpty()) {
			summary.append(" at ").append(compilerFrame);
		}
		return summary.toString();
	}

	private static void appendException(StringBuilder summary, Throwable ex) {
		String type = ex.getClass().getSimpleName();
		String message = ex.getMessage();
		if (message == null || message.isEmpty()) {
			summary.append(type);
		} else if (message.startsWith(type + ":")) {
			summary.append(message);
		} else {
			summary.append(type).append(": ").append(message);
		}
	}

	private static String compilerFrame(Throwable ex) {
		try {
			StackTraceElement[] trace = ex.getStackTrace();
			for (StackTraceElement frame : trace) {
				String className = frame.getClassName();
				if (className != null && className.startsWith("org.eclipse.jdt.internal.compiler.")) {
					return frame.toString();
				}
			}
		} catch (Throwable ignored) {
		}
		return "";
	}
}
