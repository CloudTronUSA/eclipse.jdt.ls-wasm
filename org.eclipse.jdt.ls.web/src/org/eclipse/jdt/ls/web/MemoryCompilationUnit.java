package org.eclipse.jdt.ls.web;

import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.ModuleBinding;

final class MemoryCompilationUnit implements ICompilationUnit {

	private final char[] contents;
	private final char[] fileName;
	private final char[] mainTypeName;
	private final char[][] packageName;

	static MemoryCompilationUnit from(String uri, String source) {
		return new MemoryCompilationUnit(source, fileName(uri), packageName(uri, source));
	}

	MemoryCompilationUnit alias(String mainTypeName) {
		return new MemoryCompilationUnit(new String(contents), new String(fileName), mainTypeName, packageName);
	}

	MemoryCompilationUnit(String source, String fileName) {
		this(source, fileName, new char[0][]);
	}

	MemoryCompilationUnit(String source, String fileName, char[][] packageName) {
		this(source, fileName, mainTypeName(fileName), packageName);
	}

	private MemoryCompilationUnit(String source, String fileName, String mainTypeName, char[][] packageName) {
		this.contents = source.toCharArray();
		this.fileName = fileName.toCharArray();
		this.mainTypeName = mainTypeName.toCharArray();
		this.packageName = packageName;
	}

	@Override
	public char[] getFileName() {
		return fileName;
	}

	@Override
	public char[] getContents() {
		return contents;
	}

	@Override
	public char[] getMainTypeName() {
		return mainTypeName;
	}

	@Override
	public char[][] getPackageName() {
		return packageName;
	}

	@Override
	public boolean ignoreOptionalProblems() {
		return false;
	}

	@Override
	public char[] getModuleName() {
		return null;
	}

	@Override
	public ModuleBinding module(LookupEnvironment environment) {
		return environment.module;
	}

	private static String mainTypeName(String fileName) {
		int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
		String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
		int dot = base.lastIndexOf('.');
		return dot > 0 ? base.substring(0, dot) : base;
	}

	private static String fileName(String uri) {
		if (uri == null || uri.isEmpty()) {
			return "Snippet.java";
		}
		int end = uri.length();
		int hash = uri.indexOf('#');
		if (hash >= 0) {
			end = Math.min(end, hash);
		}
		int query = uri.indexOf('?');
		if (query >= 0) {
			end = Math.min(end, query);
		}
		int slash = uri.lastIndexOf('/', end - 1);
		return slash >= 0 ? uri.substring(slash + 1, end) : uri.substring(0, end);
	}

	private static char[][] packageName(String uri, String source) {
		String packageName = expectedPackageName(uri);
		if (packageName.isEmpty()) {
			packageName = packageNameString(source);
		}
		if (packageName.isEmpty()) {
			return new char[0][];
		}
		int count = 1;
		for (int i = 0; i < packageName.length(); i++) {
			if (packageName.charAt(i) == '.') {
				count++;
			}
		}
		char[][] result = new char[count][];
		int start = 0;
		int index = 0;
		for (int i = 0; i <= packageName.length(); i++) {
			if (i == packageName.length() || packageName.charAt(i) == '.') {
				result[index++] = packageName.substring(start, i).toCharArray();
				start = i + 1;
			}
		}
		return result;
	}

	private static String expectedPackageName(String uri) {
		if (uri == null || uri.isEmpty()) {
			return "";
		}
		int end = uri.length();
		int hash = uri.indexOf('#');
		if (hash >= 0) {
			end = Math.min(end, hash);
		}
		int query = uri.indexOf('?');
		if (query >= 0) {
			end = Math.min(end, query);
		}
		String path = uri.substring(0, end);
		String relative = relativeSourcePath(path, "/src/main/java/");
		if (relative.isEmpty()) {
			relative = relativeSourcePath(path, "/src/test/java/");
		}
		if (relative.isEmpty()) {
			relative = relativeSourcePath(path, "/src/");
		}
		if (relative.isEmpty()) {
			return "";
		}
		int slash = Math.max(relative.lastIndexOf('/'), relative.lastIndexOf('\\'));
		if (slash <= 0) {
			return "";
		}
		String packagePath = relative.substring(0, slash);
		StringBuilder packageName = new StringBuilder();
		int start = 0;
		for (int index = 0; index <= packagePath.length(); index++) {
			if (index == packagePath.length() || packagePath.charAt(index) == '/' || packagePath.charAt(index) == '\\') {
				String segment = packagePath.substring(start, index);
				if (!isJavaIdentifier(segment)) {
					return "";
				}
				if (packageName.length() > 0) {
					packageName.append('.');
				}
				packageName.append(segment);
				start = index + 1;
			}
		}
		return packageName.toString();
	}

	private static String relativeSourcePath(String path, String marker) {
		int index = path.lastIndexOf(marker);
		return index >= 0 ? path.substring(index + marker.length()) : "";
	}

	private static boolean isJavaIdentifier(String value) {
		if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
			return false;
		}
		for (int index = 1; index < value.length(); index++) {
			if (!Character.isJavaIdentifierPart(value.charAt(index))) {
				return false;
			}
		}
		return true;
	}

	private static String packageNameString(String source) {
		int index = skipSpaceAndComments(source, 0);
		if (!startsWithKeyword(source, index, "package")) {
			return "";
		}
		index = skipSpaceAndComments(source, index + "package".length());
		StringBuilder packageName = new StringBuilder();
		boolean expectIdentifier = true;
		while (index < source.length()) {
			char c = source.charAt(index);
			if (c == ';') {
				return expectIdentifier ? "" : packageName.toString();
			}
			if (c == '.' && !expectIdentifier) {
				packageName.append('.');
				expectIdentifier = true;
				index++;
				continue;
			}
			if (Character.isJavaIdentifierStart(c) && expectIdentifier) {
				int start = index++;
				while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index))) {
					index++;
				}
				packageName.append(source, start, index);
				expectIdentifier = false;
				continue;
			}
			if (Character.isWhitespace(c)) {
				index++;
				continue;
			}
			return "";
		}
		return "";
	}

	private static int skipSpaceAndComments(String source, int index) {
		while (index < source.length()) {
			char c = source.charAt(index);
			if (Character.isWhitespace(c)) {
				index++;
				continue;
			}
			if (c == '/' && index + 1 < source.length()) {
				char next = source.charAt(index + 1);
				if (next == '/') {
					index += 2;
					while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r') {
						index++;
					}
					continue;
				}
				if (next == '*') {
					int end = source.indexOf("*/", index + 2);
					index = end >= 0 ? end + 2 : source.length();
					continue;
				}
			}
			return index;
		}
		return index;
	}

	private static boolean startsWithKeyword(String source, int index, String keyword) {
		int end = index + keyword.length();
		return end <= source.length()
				&& source.regionMatches(index, keyword, 0, keyword.length())
				&& (index == 0 || !Character.isJavaIdentifierPart(source.charAt(index - 1)))
				&& (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
	}

	String[] topLevelTypeNames() {
		String source = new String(contents);
		String[] names = new String[4];
		int count = 0;
		int depth = 0;
		for (int index = 0; index < source.length();) {
			index = skipSpaceCommentsStringsAndAnnotations(source, index);
			if (index >= source.length()) {
				break;
			}
			char c = source.charAt(index);
			if (c == '{') {
				depth++;
				index++;
				continue;
			}
			if (c == '}') {
				if (depth > 0) {
					depth--;
				}
				index++;
				continue;
			}
			if (depth == 0 && isTypeKeyword(source, index)) {
				int nameStart = skipSpaceAndComments(source, index + keywordLength(source, index));
				if (nameStart < source.length() && Character.isJavaIdentifierStart(source.charAt(nameStart))) {
					int nameEnd = nameStart + 1;
					while (nameEnd < source.length() && Character.isJavaIdentifierPart(source.charAt(nameEnd))) {
						nameEnd++;
					}
					String name = source.substring(nameStart, nameEnd);
					if (!contains(names, count, name)) {
						if (count == names.length) {
							String[] expanded = new String[names.length * 2];
							System.arraycopy(names, 0, expanded, 0, names.length);
							names = expanded;
						}
						names[count++] = name;
					}
					index = nameEnd;
					continue;
				}
			}
			index++;
		}
		String[] result = new String[count];
		System.arraycopy(names, 0, result, 0, count);
		return result;
	}

	private static boolean contains(String[] values, int count, String value) {
		for (int i = 0; i < count; i++) {
			if (values[i].equals(value)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isTypeKeyword(String source, int index) {
		return startsWithKeyword(source, index, "class")
				|| startsWithKeyword(source, index, "interface")
				|| startsWithKeyword(source, index, "enum")
				|| startsWithKeyword(source, index, "record");
	}

	private static int keywordLength(String source, int index) {
		if (startsWithKeyword(source, index, "interface")) {
			return "interface".length();
		}
		if (startsWithKeyword(source, index, "record")) {
			return "record".length();
		}
		if (startsWithKeyword(source, index, "class")) {
			return "class".length();
		}
		return "enum".length();
	}

	private static int skipSpaceCommentsStringsAndAnnotations(String source, int index) {
		while (index < source.length()) {
			int skipped = skipSpaceAndComments(source, index);
			if (skipped != index) {
				index = skipped;
				continue;
			}
			char c = source.charAt(index);
			if (c == '"' || c == '\'') {
				index = skipQuoted(source, index, c);
				continue;
			}
			if (c == '@') {
				index++;
				while (index < source.length() && (Character.isJavaIdentifierPart(source.charAt(index)) || source.charAt(index) == '.')) {
					index++;
				}
				continue;
			}
			return index;
		}
		return index;
	}

	private static int skipQuoted(String source, int index, char quote) {
		index++;
		while (index < source.length()) {
			char c = source.charAt(index++);
			if (c == '\\' && index < source.length()) {
				index++;
				continue;
			}
			if (c == quote) {
				return index;
			}
		}
		return index;
	}
}
