package org.eclipse.jdt.ls.browser;

import org.teavm.jso.JSExport;

public final class BrowserJdtLs {

	private static final EcjDiagnosticsEngine ENGINE = new EcjDiagnosticsEngine();

	private BrowserJdtLs() {
	}

	public static void main(String[] args) {
	}

	@JSExport
	public static String lint(String uri, String source) {
		return ENGINE.lint(uri, source);
	}

	@JSExport
	public static String handle(String payload) {
		return BrowserLspEndpoint.handle(payload, ENGINE);
	}
}
