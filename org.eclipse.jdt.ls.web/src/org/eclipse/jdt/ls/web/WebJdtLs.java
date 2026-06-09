package org.eclipse.jdt.ls.web;

import org.teavm.jso.JSExport;

public final class WebJdtLs {

	private static final EcjDiagnosticsEngine ENGINE = new EcjDiagnosticsEngine();

	private WebJdtLs() {
	}

	public static void main(String[] args) {
	}

	@JSExport
	public static String lint(String uri, String source) {
		return ENGINE.lint(uri, source);
	}

	@JSExport
	public static String lintProcessing(String entrypointUri, String entrypointSource, String additionalPdesJson) {
		return ENGINE.lintProcessing(entrypointUri, entrypointSource,
				JsonSupport.objectsInArrayField(additionalPdesJson, "sources"));
	}

	@JSExport
	public static String handle(String payload) {
		return WebLspEndpoint.handle(payload, ENGINE);
	}
}
