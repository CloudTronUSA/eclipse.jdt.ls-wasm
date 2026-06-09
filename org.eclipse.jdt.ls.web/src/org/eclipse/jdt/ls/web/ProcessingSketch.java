package org.eclipse.jdt.ls.web;

import java.util.ArrayList;
import java.util.List;

final class ProcessingSketch {

	private static final String SKETCH_CLASS = "Sketch";

	final String uri;
	final String source;
	private final SourceSegment[] segments;
	private final String[] sourceUris;

	private ProcessingSketch(String uri, String source, SourceSegment[] segments, String[] sourceUris) {
		this.uri = uri;
		this.source = source;
		this.segments = segments;
		this.sourceUris = sourceUris;
	}

	static ProcessingSketch from(String entrypointUri, String entrypointSource, String[] additionalPdes) {
		List<PdeSource> pdes = new ArrayList<>();
		pdes.add(processPde(entrypointUri, entrypointSource));
		for (String pde : additionalPdes) {
			String uri = JsonSupport.stringField(pde, "uri");
			if (uri.isEmpty()) {
				uri = entrypointUri;
			}
			pdes.add(processPde(uri, JsonSupport.lastStringField(pde, "text")));
		}

		StringBuilder imports = new StringBuilder();
		List<SourceSegment> segments = new ArrayList<>();
		appendImplicitImports(imports);
		for (PdeSource pde : pdes) {
			for (ImportLine imported : pde.imports) {
				int generatedLine = lineCount(imports.toString());
				imports.append(imported.text).append('\n');
				segments.add(new SourceSegment(imported.uri, generatedLine, imported.sourceLine, 1));
			}
		}

		String prefix = imports.toString()
				+ "\npublic class " + SKETCH_CLASS + " extends processing.core.PApplet {\n";
		StringBuilder body = new StringBuilder();
		int generatedLine = lineCount(prefix);
		for (PdeSource pde : pdes) {
			if (pde.lineCount > 0) {
				segments.add(new SourceSegment(pde.uri, generatedLine, 0, pde.lineCount));
				body.append(pde.body);
				generatedLine += pde.lineCount;
			}
		}

		String source = prefix + body + "}\n";
		return new ProcessingSketch(syntheticUri(entrypointUri), source,
				segments.toArray(new SourceSegment[0]), sourceUris(pdes));
	}

	MemoryCompilationUnit compilationUnit() {
		return new MemoryCompilationUnit(source, SKETCH_CLASS + ".java");
	}

	String[] sourceUris() {
		return sourceUris;
	}

	List<MappedDiagnostic> mapDiagnostics(List<EcjDiagnosticsEngine.DiagnosticData> diagnostics) {
		List<MappedDiagnostic> mapped = new ArrayList<>(diagnostics.size());
		for (EcjDiagnosticsEngine.DiagnosticData diagnostic : diagnostics) {
			SourceSegment segment = segmentFor(diagnostic.startLine);
			if (segment == null) {
				continue;
			}
			int mappedStartLine = segment.sourceStartLine + diagnostic.startLine - segment.generatedStartLine;
			int mappedEndLine = segment.sourceStartLine
					+ Math.min(diagnostic.endLine, segment.generatedStartLine + segment.lineCount - 1)
					- segment.generatedStartLine;
			if (mappedEndLine < mappedStartLine) {
				mappedEndLine = mappedStartLine;
			}
			mapped.add(new MappedDiagnostic(segment.uri,
					new EcjDiagnosticsEngine.DiagnosticData(mappedStartLine, diagnostic.startCharacter,
							mappedEndLine, diagnostic.endCharacter, diagnostic.severity, diagnostic.code,
							diagnostic.message)));
		}
		return mapped;
	}

	private SourceSegment segmentFor(int generatedLine) {
		for (SourceSegment segment : segments) {
			if (generatedLine >= segment.generatedStartLine
					&& generatedLine < segment.generatedStartLine + segment.lineCount) {
				return segment;
			}
		}
		return null;
	}

	private static void appendImplicitImports(StringBuilder imports) {
		imports.append("import processing.core.*;\n");
		imports.append("import processing.data.*;\n");
		imports.append("import processing.event.*;\n");
		imports.append("import processing.opengl.*;\n");
		imports.append("import java.util.*;\n");
		imports.append("import java.io.*;\n");
	}

	private static PdeSource processPde(String uri, String source) {
		PdeSource pde = new PdeSource(uri);
		if (source == null || source.isEmpty()) {
			return pde;
		}
		int sourceLine = 0;
		int index = 0;
		while (index < source.length()) {
			int lineEnd = source.indexOf('\n', index);
			if (lineEnd < 0) {
				lineEnd = source.length();
			}
			String line = source.substring(index, lineEnd);
			String trimmed = line.trim();
			if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
				pde.imports.add(new ImportLine(uri, sourceLine, trimmed));
				pde.body.append('\n');
			} else {
				pde.body.append(processingMethodLine(line, trimmed));
				pde.body.append('\n');
			}
			pde.lineCount++;
			sourceLine++;
			index = lineEnd + 1;
		}
		return pde;
	}

	private static String processingMethodLine(String line, String trimmed) {
		if (trimmed.startsWith("public ") || trimmed.startsWith("private ") || trimmed.startsWith("protected ")) {
			return line;
		}
		if (!isProcessingLifecycleMethod(trimmed)) {
			return line;
		}
		int leading = line.indexOf(trimmed);
		return line.substring(0, leading) + "public " + trimmed;
	}

	private static boolean isProcessingLifecycleMethod(String trimmed) {
		String[] names = {
				"settings",
				"setup",
				"draw",
				"mousePressed",
				"mouseReleased",
				"mouseClicked",
				"mouseDragged",
				"mouseMoved",
				"mouseEntered",
				"mouseExited",
				"mouseWheel",
				"keyPressed",
				"keyReleased",
				"keyTyped"
		};
		for (String name : names) {
			if (trimmed.startsWith("void " + name + "(")) {
				return true;
			}
		}
		return false;
	}

	private static String syntheticUri(String entrypointUri) {
		if (entrypointUri == null || entrypointUri.isEmpty()) {
			return "memory://Sketch.java";
		}
		int slash = entrypointUri.lastIndexOf('/');
		if (slash < 0) {
			return "memory://Sketch.java";
		}
		return entrypointUri.substring(0, slash + 1) + SKETCH_CLASS + ".java";
	}

	private static String[] sourceUris(List<PdeSource> pdes) {
		List<String> uris = new ArrayList<>();
		for (PdeSource pde : pdes) {
			if (!uris.contains(pde.uri)) {
				uris.add(pde.uri);
			}
		}
		return uris.toArray(new String[0]);
	}

	private static int lineCount(String source) {
		int lines = 0;
		for (int i = 0; i < source.length(); i++) {
			if (source.charAt(i) == '\n') {
				lines++;
			}
		}
		return lines;
	}

	static final class MappedDiagnostic {
		final String uri;
		final EcjDiagnosticsEngine.DiagnosticData diagnostic;

		MappedDiagnostic(String uri, EcjDiagnosticsEngine.DiagnosticData diagnostic) {
			this.uri = uri;
			this.diagnostic = diagnostic;
		}
	}

	private static final class PdeSource {
		final String uri;
		final StringBuilder body = new StringBuilder();
		final List<ImportLine> imports = new ArrayList<>();
		int lineCount;

		PdeSource(String uri) {
			this.uri = uri;
		}
	}

	private static final class ImportLine {
		final String uri;
		final int sourceLine;
		final String text;

		ImportLine(String uri, int sourceLine, String text) {
			this.uri = uri;
			this.sourceLine = sourceLine;
			this.text = text;
		}
	}

	private static final class SourceSegment {
		final String uri;
		final int generatedStartLine;
		final int sourceStartLine;
		final int lineCount;

		SourceSegment(String uri, int generatedStartLine, int sourceStartLine, int lineCount) {
			this.uri = uri;
			this.generatedStartLine = generatedStartLine;
			this.sourceStartLine = sourceStartLine;
			this.lineCount = lineCount;
		}
	}
}
