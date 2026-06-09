package org.eclipse.jdt.ls.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BrowserCompilerConfigurationTest {

	@Test
	void preservesIndependentCompilerLevelsFromConfigurationPayload() {
		BrowserCompilerConfiguration configuration = BrowserCompilerConfiguration.DEFAULT.withSettings(
				"{\"params\":{\"settings\":{\"java\":{\"settings\":{"
						+ "\"org.eclipse.jdt.core.compiler.source\":\"17\","
						+ "\"org.eclipse.jdt.core.compiler.compliance\":\"21\","
						+ "\"org.eclipse.jdt.core.compiler.codegen.targetPlatform\":\"11\""
						+ "}}}}}");

		assertEquals(ClassFileConstantsShim.JDK17, configuration.sourceLevel);
		assertEquals(ClassFileConstantsShim.JDK21, configuration.complianceLevel);
		assertEquals(ClassFileConstantsShim.JDK11, configuration.targetJdk);
	}

	@Test
	void leavesUnspecifiedCompilerLevelsUnchanged() {
		BrowserCompilerConfiguration configuration = BrowserCompilerConfiguration.DEFAULT
				.withSettings("{\"params\":{\"settings\":{\"java\":{\"settings\":{"
						+ "\"org.eclipse.jdt.core.compiler.codegen.targetPlatform\":\"1.8\""
						+ "}}}}}");

		assertEquals(ClassFileConstantsShim.JDK17, configuration.sourceLevel);
		assertEquals(ClassFileConstantsShim.JDK17, configuration.complianceLevel);
		assertEquals(ClassFileConstantsShim.JDK1_8, configuration.targetJdk);
	}
}
