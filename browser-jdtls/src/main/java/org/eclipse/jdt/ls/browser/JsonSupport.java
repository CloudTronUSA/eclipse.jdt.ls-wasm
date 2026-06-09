package org.eclipse.jdt.ls.browser;

final class JsonSupport {

	private JsonSupport() {
	}

	static void appendString(StringBuilder json, String value) {
		json.append('"');
		if (value != null) {
			for (int i = 0; i < value.length(); i++) {
				char c = value.charAt(i);
				switch (c) {
					case '\\':
					case '"':
						json.append('\\').append(c);
						break;
					case '\n':
						json.append("\\n");
						break;
					case '\r':
						json.append("\\r");
						break;
					case '\t':
						json.append("\\t");
						break;
					default:
						if (c < 0x20) {
							json.append("\\u");
							String hex = Integer.toHexString(c);
							for (int j = hex.length(); j < 4; j++) {
								json.append('0');
							}
							json.append(hex);
						} else {
							json.append(c);
						}
				}
			}
		}
		json.append('"');
	}

	static String stringField(String json, String name) {
		int nameIndex = json.indexOf('"' + name + '"');
		if (nameIndex < 0) {
			return "";
		}
		return stringAfterColon(json, nameIndex + name.length() + 2);
	}

	static String lastStringField(String json, String name) {
		int nameIndex = json.lastIndexOf('"' + name + '"');
		if (nameIndex < 0) {
			return "";
		}
		return stringAfterColon(json, nameIndex + name.length() + 2);
	}

	static String idField(String json) {
		int nameIndex = json.indexOf("\"id\"");
		if (nameIndex < 0) {
			return "null";
		}
		int colon = json.indexOf(':', nameIndex);
		int index = colon + 1;
		while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
			index++;
		}
		if (index < json.length() && json.charAt(index) == '"') {
			StringBuilder out = new StringBuilder();
			appendString(out, readString(json, index));
			return out.toString();
		}
		int end = index;
		while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) {
			end++;
		}
		return end > index ? json.substring(index, end) : "null";
	}

	static String[] objectsInArrayField(String json, String name) {
		int nameIndex = json.indexOf('"' + name + '"');
		if (nameIndex < 0) {
			return new String[0];
		}
		int colon = json.indexOf(':', nameIndex + name.length() + 2);
		if (colon < 0) {
			return new String[0];
		}
		int arrayStart = json.indexOf('[', colon + 1);
		if (arrayStart < 0) {
			return new String[0];
		}
		String[] objects = new String[4];
		int count = 0;
		for (int index = arrayStart + 1; index < json.length();) {
			index = skipWhitespace(json, index);
			if (index >= json.length() || json.charAt(index) == ']') {
				break;
			}
			if (json.charAt(index) != '{') {
				index++;
				continue;
			}
			int end = matchingObjectEnd(json, index);
			if (end < 0) {
				break;
			}
			if (count == objects.length) {
				String[] expanded = new String[objects.length * 2];
				System.arraycopy(objects, 0, expanded, 0, objects.length);
				objects = expanded;
			}
			objects[count++] = json.substring(index, end + 1);
			index = end + 1;
		}
		String[] result = new String[count];
		System.arraycopy(objects, 0, result, 0, count);
		return result;
	}

	static int intField(String json, String name) {
		int nameIndex = json.indexOf('"' + name + '"');
		if (nameIndex < 0) {
			return 0;
		}
		int colon = json.indexOf(':', nameIndex + name.length() + 2);
		if (colon < 0) {
			return 0;
		}
		int index = skipWhitespace(json, colon + 1);
		int end = index;
		while (end < json.length() && "-0123456789".indexOf(json.charAt(end)) >= 0) {
			end++;
		}
		if (end == index) {
			return 0;
		}
		try {
			return Integer.parseInt(json.substring(index, end));
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private static String stringAfterColon(String json, int from) {
		int colon = json.indexOf(':', from);
		if (colon < 0) {
			return "";
		}
		int quote = json.indexOf('"', colon + 1);
		return quote >= 0 ? readString(json, quote) : "";
	}

	private static int matchingObjectEnd(String json, int objectStart) {
		int depth = 0;
		boolean inString = false;
		for (int i = objectStart; i < json.length(); i++) {
			char c = json.charAt(i);
			if (inString) {
				if (c == '\\') {
					i++;
				} else if (c == '"') {
					inString = false;
				}
				continue;
			}
			if (c == '"') {
				inString = true;
				continue;
			}
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	private static int skipWhitespace(String json, int index) {
		while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
			index++;
		}
		return index;
	}

	private static String readString(String json, int quote) {
		StringBuilder value = new StringBuilder();
		for (int i = quote + 1; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '"') {
				return value.toString();
			}
			if (c == '\\' && i + 1 < json.length()) {
				char escaped = json.charAt(++i);
				switch (escaped) {
					case 'n':
						value.append('\n');
						break;
					case 'r':
						value.append('\r');
						break;
					case 't':
						value.append('\t');
						break;
					case '"':
					case '\\':
					case '/':
						value.append(escaped);
						break;
					case 'u':
						if (i + 4 < json.length()) {
							int codePoint = hexValue(json, i + 1, i + 5);
							if (codePoint >= 0) {
								value.append((char) codePoint);
								i += 4;
								break;
							}
						}
						value.append('u');
						break;
					default:
						value.append(escaped);
				}
			} else {
				value.append(c);
			}
		}
		return value.toString();
	}

	private static int hexValue(String value, int start, int end) {
		int result = 0;
		for (int i = start; i < end; i++) {
			int digit = hexDigit(value.charAt(i));
			if (digit < 0) {
				return -1;
			}
			result = result * 16 + digit;
		}
		return result;
	}

	private static int hexDigit(char c) {
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}
		if (c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}
		return -1;
	}
}
