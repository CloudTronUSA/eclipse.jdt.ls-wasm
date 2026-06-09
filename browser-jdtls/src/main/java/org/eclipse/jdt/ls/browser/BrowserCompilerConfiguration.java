package org.eclipse.jdt.ls.browser;

import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

final class BrowserCompilerConfiguration {

	static final BrowserCompilerConfiguration DEFAULT = new BrowserCompilerConfiguration(
			ClassFileConstantsShim.JDK17,
			ClassFileConstantsShim.JDK17,
			ClassFileConstantsShim.JDK17);

	final long sourceLevel;
	final long complianceLevel;
	final long targetJdk;

	private BrowserCompilerConfiguration(long sourceLevel, long complianceLevel, long targetJdk) {
		this.sourceLevel = sourceLevel;
		this.complianceLevel = complianceLevel;
		this.targetJdk = targetJdk;
	}

	CompilerOptions compilerOptions() {
		CompilerOptions options = new CompilerOptions();
		options.sourceLevel = sourceLevel;
		options.complianceLevel = complianceLevel;
		options.targetJDK = targetJdk;
		options.parseLiteralExpressionsAsConstants = true;
		options.performMethodsFullRecovery = true;
		options.performStatementsRecovery = true;
		options.maxProblemsPerUnit = 200;
		options.generateClassFiles = false;
		options.processAnnotations = false;
		options.storeAnnotations = false;
		options.analyseResourceLeaks = false;
		options.isAnnotationBasedResourceAnalysisEnabled = false;
		return options;
	}

	BrowserCompilerConfiguration withSource(String version) {
		long level = level(version, sourceLevel);
		return new BrowserCompilerConfiguration(level, level, level);
	}

	BrowserCompilerConfiguration withSettings(String payload) {
		long source = level(JsonSupport.stringField(payload, "org.eclipse.jdt.core.compiler.source"), sourceLevel);
		long compliance = level(JsonSupport.stringField(payload, "org.eclipse.jdt.core.compiler.compliance"),
				complianceLevel);
		long target = level(JsonSupport.stringField(payload, "org.eclipse.jdt.core.compiler.codegen.targetPlatform"),
				targetJdk);
		return new BrowserCompilerConfiguration(source, compliance, target);
	}

	private static long level(String value, long fallback) {
		if (value == null) {
			return fallback;
		}
		if ("1.8".equals(value) || "8".equals(value)) {
			return ClassFileConstantsShim.JDK1_8;
		}
		if ("11".equals(value)) {
			return ClassFileConstantsShim.JDK11;
		}
		if ("17".equals(value)) {
			return ClassFileConstantsShim.JDK17;
		}
		if ("21".equals(value)) {
			return ClassFileConstantsShim.JDK21;
		}
		return fallback;
	}
}
