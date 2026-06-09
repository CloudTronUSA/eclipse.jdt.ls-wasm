package org.eclipse.jdt.ls.browser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class MemoryCompilationUnitTest {

	@Test
	void infersPackageNameFromCommonSourceRoot() {
		MemoryCompilationUnit unit = MemoryCompilationUnit.from(
				"file:///workspace/project/src/main/java/com/example/App.java",
				"package wrong; public class App {}");

		assertArrayEquals(new char[][] { "com".toCharArray(), "example".toCharArray() }, unit.getPackageName());
	}

	@Test
	void fallsBackToDeclaredPackageOutsideKnownSourceRoots() {
		MemoryCompilationUnit unit = MemoryCompilationUnit.from(
				"file:///workspace/project/demo/App.java",
				"package demo; public class App {}");

		assertArrayEquals(new char[][] { "demo".toCharArray() }, unit.getPackageName());
	}

	@Test
	void ignoresInvalidPackageSegmentsInSourcePath() {
		MemoryCompilationUnit unit = MemoryCompilationUnit.from(
				"file:///workspace/project/src/main/java/generated-code/App.java",
				"package fallback; public class App {}");

		assertArrayEquals(new char[][] { "fallback".toCharArray() }, unit.getPackageName());
	}
}
