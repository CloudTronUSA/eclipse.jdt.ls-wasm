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
				segments.add(new SourceSegment(imported.uri, generatedLine, imported.sourceLine, 1, null));
			}
		}

		String prefix = imports.toString()
				+ "\npublic class " + SKETCH_CLASS + " extends processing.core.PApplet {\n";
		StringBuilder body = new StringBuilder();
		int generatedLine = lineCount(prefix);
		for (PdeSource pde : pdes) {
			if (pde.lineCount > 0) {
				segments.add(new SourceSegment(pde.uri, generatedLine, 0, pde.lineCount,
						pde.lineMappings.toArray(new int[0][])));
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
			int generatedEndLine = Math.min(diagnostic.endLine, segment.generatedStartLine + segment.lineCount - 1);
			int mappedEndLine = segment.sourceStartLine
					+ generatedEndLine - segment.generatedStartLine;
			if (mappedEndLine < mappedStartLine) {
				mappedEndLine = mappedStartLine;
			}
			mapped.add(new MappedDiagnostic(segment.uri,
					new EcjDiagnosticsEngine.DiagnosticData(mappedStartLine,
							segment.mapCharacter(diagnostic.startLine, diagnostic.startCharacter),
							mappedEndLine, segment.mapCharacter(generatedEndLine, diagnostic.endCharacter),
							diagnostic.severity, diagnostic.code,
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
				pde.lineMappings.add(null);
			} else {
				ProcessedLine processed = processingLine(line, trimmed, pde.inBlockComment);
				pde.inBlockComment = processed.inBlockComment;
				pde.body.append(processed.text);
				pde.body.append('\n');
				pde.lineMappings.add(processed.mapping);
			}
			pde.lineCount++;
			sourceLine++;
			index = lineEnd + 1;
		}
		return pde;
	}

	private static ProcessedLine processingLine(String line, String trimmed, boolean inBlockComment) {
		boolean insertPublic = shouldInsertPublic(trimmed);
		StringBuilder output = new StringBuilder(line.length() + 8);
		List<Integer> mapping = new ArrayList<>();
		mapping.add(Integer.valueOf(0));
		boolean[] blockComment = new boolean[] { inBlockComment };
		int publicOffset = insertPublic ? line.indexOf(trimmed) : -1;
		int index = 0;
		while (index < line.length()) {
			if (index == publicOffset) {
				appendSynthetic(output, mapping, "public ", index);
			}
			index = appendProcessingToken(line, index, output, mapping, blockComment);
		}
		if (line.length() == publicOffset) {
			appendSynthetic(output, mapping, "public ", line.length());
		}
		return new ProcessedLine(output.toString(), toMapping(output, mapping), blockComment[0]);
	}

	private static boolean shouldInsertPublic(String trimmed) {
		if (trimmed.startsWith("public ") || trimmed.startsWith("private ") || trimmed.startsWith("protected ")) {
			return false;
		}
		if (!isProcessingLifecycleMethod(trimmed)) {
			return false;
		}
		return true;
	}

	private static int appendProcessingToken(String line, int index, StringBuilder output, List<Integer> mapping,
			boolean[] inBlockComment) {
		char current = line.charAt(index);
		if (inBlockComment[0]) {
			appendOriginal(output, mapping, current, index);
			if (current == '*' && index + 1 < line.length() && line.charAt(index + 1) == '/') {
				appendOriginal(output, mapping, '/', index + 1);
				inBlockComment[0] = false;
				return index + 2;
			}
			return index + 1;
		}
		if (current == '/' && index + 1 < line.length()) {
			char next = line.charAt(index + 1);
			if (next == '/') {
				appendOriginalRest(line, index, output, mapping);
				return line.length();
			}
			if (next == '*') {
				appendOriginal(output, mapping, current, index);
				appendOriginal(output, mapping, next, index + 1);
				inBlockComment[0] = true;
				return index + 2;
			}
		}
		if (current == '"' || current == '\'') {
			return appendQuoted(line, index, output, mapping, current);
		}
		if (startsDecimalLiteral(line, index)) {
			return appendDecimalLiteral(line, index, output, mapping);
		}
		appendOriginal(output, mapping, current, index);
		return index + 1;
	}

	private static int appendQuoted(String line, int index, StringBuilder output, List<Integer> mapping, char quote) {
		appendOriginal(output, mapping, quote, index++);
		boolean escaped = false;
		while (index < line.length()) {
			char current = line.charAt(index);
			appendOriginal(output, mapping, current, index);
			index++;
			if (escaped) {
				escaped = false;
			} else if (current == '\\') {
				escaped = true;
			} else if (current == quote) {
				break;
			}
		}
		return index;
	}

	private static boolean startsDecimalLiteral(String line, int index) {
		char current = line.charAt(index);
		if (current == '.') {
			return index + 1 < line.length() && Character.isDigit(line.charAt(index + 1))
					&& !isNumericPart(previousChar(line, index));
		}
		if (!Character.isDigit(current)) {
			return false;
		}
		char previous = previousChar(line, index);
		return !isNumericPart(previous) && previous != '.';
	}

	private static int appendDecimalLiteral(String line, int index, StringBuilder output, List<Integer> mapping) {
		int start = index;
		boolean decimal = false;
		boolean hexadecimal = index + 1 < line.length() && line.charAt(index) == '0'
				&& (line.charAt(index + 1) == 'x' || line.charAt(index + 1) == 'X');
		if (line.charAt(index) == '.') {
			decimal = true;
			appendOriginal(output, mapping, '.', index++);
			index = appendDigits(line, index, output, mapping);
		} else {
			index = appendDigits(line, index, output, mapping);
			if (!hexadecimal && index < line.length() && line.charAt(index) == '.'
					&& (index + 1 >= line.length() || line.charAt(index + 1) != '.')) {
				decimal = true;
				appendOriginal(output, mapping, '.', index++);
				index = appendDigits(line, index, output, mapping);
			}
		}
		if (!hexadecimal && index < line.length() && (line.charAt(index) == 'e' || line.charAt(index) == 'E')) {
			int exponent = index;
			int next = exponent + 1;
			if (next < line.length() && (line.charAt(next) == '+' || line.charAt(next) == '-')) {
				next++;
			}
			if (next < line.length() && Character.isDigit(line.charAt(next))) {
				decimal = true;
				appendOriginal(output, mapping, line.charAt(index), index++);
				if (index < line.length() && (line.charAt(index) == '+' || line.charAt(index) == '-')) {
					appendOriginal(output, mapping, line.charAt(index), index++);
				}
				index = appendDigits(line, index, output, mapping);
			}
		}
		if (index < line.length() && isFloatSuffix(line.charAt(index))) {
			appendOriginal(output, mapping, line.charAt(index), index++);
			return index;
		}
		if (decimal && !isIdentifierPart(nextChar(line, index))) {
			appendSynthetic(output, mapping, "f", index);
		}
		if (index == start) {
			appendOriginal(output, mapping, line.charAt(index), index++);
		}
		return index;
	}

	private static int appendDigits(String line, int index, StringBuilder output, List<Integer> mapping) {
		while (index < line.length()) {
			char current = line.charAt(index);
			if (!Character.isDigit(current) && current != '_') {
				break;
			}
			appendOriginal(output, mapping, current, index++);
		}
		return index;
	}

	private static boolean isFloatSuffix(char value) {
		return value == 'f' || value == 'F' || value == 'd' || value == 'D';
	}

	private static char previousChar(String line, int index) {
		return index > 0 ? line.charAt(index - 1) : 0;
	}

	private static char nextChar(String line, int index) {
		return index < line.length() ? line.charAt(index) : 0;
	}

	private static boolean isNumericPart(char value) {
		return Character.isLetterOrDigit(value) || value == '_' || value == '$';
	}

	private static boolean isIdentifierPart(char value) {
		return Character.isLetterOrDigit(value) || value == '_' || value == '$';
	}

	private static void appendOriginalRest(String line, int index, StringBuilder output, List<Integer> mapping) {
		while (index < line.length()) {
			appendOriginal(output, mapping, line.charAt(index), index++);
		}
	}

	private static void appendOriginal(StringBuilder output, List<Integer> mapping, char value, int originalIndex) {
		output.append(value);
		mapping.add(Integer.valueOf(originalIndex + 1));
	}

	private static void appendSynthetic(StringBuilder output, List<Integer> mapping, String value, int originalIndex) {
		for (int i = 0; i < value.length(); i++) {
			output.append(value.charAt(i));
			mapping.add(Integer.valueOf(originalIndex));
		}
	}

	private static int[] toMapping(StringBuilder output, List<Integer> mapping) {
		if (output.length() + 1 != mapping.size()) {
			return null;
		}
		boolean identity = true;
		int[] result = new int[mapping.size()];
		for (int i = 0; i < mapping.size(); i++) {
			result[i] = mapping.get(i).intValue();
			if (result[i] != i) {
				identity = false;
			}
		}
		return identity ? null : result;
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
		final List<int[]> lineMappings = new ArrayList<>();
		boolean inBlockComment;
		int lineCount;

		PdeSource(String uri) {
			this.uri = uri;
		}
	}

	private static final class ProcessedLine {
		final String text;
		final int[] mapping;
		final boolean inBlockComment;

		ProcessedLine(String text, int[] mapping, boolean inBlockComment) {
			this.text = text;
			this.mapping = mapping;
			this.inBlockComment = inBlockComment;
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
		final int[][] lineMappings;

		SourceSegment(String uri, int generatedStartLine, int sourceStartLine, int lineCount, int[][] lineMappings) {
			this.uri = uri;
			this.generatedStartLine = generatedStartLine;
			this.sourceStartLine = sourceStartLine;
			this.lineCount = lineCount;
			this.lineMappings = lineMappings;
		}

		int mapCharacter(int generatedLine, int generatedCharacter) {
			if (lineMappings == null) {
				return generatedCharacter;
			}
			int lineIndex = generatedLine - generatedStartLine;
			if (lineIndex < 0 || lineIndex >= lineMappings.length) {
				return generatedCharacter;
			}
			int[] mapping = lineMappings[lineIndex];
			if (mapping == null || mapping.length == 0) {
				return generatedCharacter;
			}
			if (generatedCharacter < 0) {
				return 0;
			}
			if (generatedCharacter < mapping.length) {
				return mapping[generatedCharacter];
			}
			return mapping[mapping.length - 1] + generatedCharacter - mapping.length + 1;
		}
	}
}
