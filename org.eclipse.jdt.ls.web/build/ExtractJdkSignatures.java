import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;

public final class ExtractJdkSignatures {

	private static final String INDEX_RESOURCE = "org/eclipse/jdt/ls/web/internal/resources/jdk-signature.resources";

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			throw new IllegalArgumentException("Usage: ExtractJdkSignatures <outputDirectory> <release>");
		}
		Path outputDirectory = Path.of(args[0]);
		char release = releaseLetter(args[1]);
		Path ctSym = Path.of(System.getProperty("java.home"), "lib", "ct.sym");
		if (!Files.isRegularFile(ctSym)) {
			Path parentHome = Path.of(System.getProperty("java.home")).getParent();
			if (parentHome != null) {
				ctSym = parentHome.resolve("lib").resolve("ct.sym");
			}
		}
		if (!Files.isRegularFile(ctSym)) {
			throw new IOException("Cannot find ct.sym under java.home=" + System.getProperty("java.home"));
		}

		Map<String, String> resources = new TreeMap<>();
		try (JarFile jar = new JarFile(ctSym.toFile())) {
			var entries = jar.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				if (entry.isDirectory()) {
					continue;
				}
				String name = entry.getName();
				if (!name.endsWith(".sig")) {
					continue;
				}
				String[] parts = name.split("/", 3);
				if (parts.length != 3 || parts[0].indexOf(release) < 0) {
					continue;
				}
				String resourceName = parts[2].substring(0, parts[2].length() - ".sig".length()) + ".class";
				if ("module-info.class".equals(resourceName)) {
					continue;
				}
				resources.put(resourceName, name);
			}

			for (Map.Entry<String, String> resource : resources.entrySet()) {
				Path output = outputDirectory.resolve(resource.getKey());
				Files.createDirectories(output.getParent());
				try (InputStream input = jar.getInputStream(jar.getEntry(resource.getValue()));
						OutputStream outputStream = Files.newOutputStream(output)) {
					input.transferTo(outputStream);
				}
			}
		}

		Path index = outputDirectory.resolve(INDEX_RESOURCE);
		Files.createDirectories(index.getParent());
		try (BufferedWriter writer = Files.newBufferedWriter(index, StandardCharsets.UTF_8)) {
			for (String resourceName : resources.keySet()) {
				writer.write(resourceName);
				writer.newLine();
			}
		}
		System.out.println("Extracted " + resources.size() + " JDK signature resources for release " + args[1]);
	}

	private static char releaseLetter(String value) {
		int release = Integer.parseInt(value);
		if (release == 8) {
			return '8';
		}
		if (release == 9) {
			return '9';
		}
		if (release >= 10 && release <= 35) {
			return (char) ('A' + release - 10);
		}
		throw new IllegalArgumentException("Unsupported release: " + value);
	}
}
