package org.eclipse.jdt.ls.browser.resources;

import java.util.ArrayList;
import java.util.List;

import org.teavm.classlib.ResourceSupplier;
import org.teavm.classlib.ResourceSupplierContext;

public final class EcjResourceSupplier implements ResourceSupplier {

	@Override
	public String[] supplyResources(ResourceSupplierContext context) {
		String[] unicodeDirectories = {
				"unicode",
				"unicode6",
				"unicode6_2",
				"unicode7",
				"unicode8",
				"unicode10",
				"unicode11",
				"unicode12_1"
		};
		List<String> resources = new ArrayList<>();
		String prefix = "org/eclipse/jdt/internal/compiler/parser/";
		for (int i = 1; i <= 24; i++) {
			resources.add(prefix + "parser" + i + ".rsc");
		}
		resources.add(prefix + "readableNames.props");
		resources.add("org/eclipse/jdt/internal/compiler/problem/messages.properties");
		resources.add("org/eclipse/jdt/internal/compiler/messages.properties");
		resources.add("org/eclipse/jdt/internal/compiler/batch/messages.properties");
		for (String directory : unicodeDirectories) {
			resources.add(prefix + directory + "/start0.rsc");
			resources.add(prefix + directory + "/start1.rsc");
			resources.add(prefix + directory + "/start2.rsc");
			resources.add(prefix + directory + "/part0.rsc");
			resources.add(prefix + directory + "/part1.rsc");
			resources.add(prefix + directory + "/part2.rsc");
			resources.add(prefix + directory + "/part14.rsc");
		}
		resources.add(prefix + "unicode13/start0.rsc");
		resources.add(prefix + "unicode13/start1.rsc");
		resources.add(prefix + "unicode13/start2.rsc");
		resources.add(prefix + "unicode13/start3.rsc");
		resources.add(prefix + "unicode13/part0.rsc");
		resources.add(prefix + "unicode13/part1.rsc");
		resources.add(prefix + "unicode13/part2.rsc");
		resources.add(prefix + "unicode13/part3.rsc");
		resources.add(prefix + "unicode13/part14.rsc");
		return resources.toArray(new String[0]);
	}
}
