package org.eclipse.jdt.ls.browser;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;

final class ClassFileConstantsShim {

	static final long JDK1_8 = ClassFileConstants.JDK1_8;
	static final long JDK11 = ClassFileConstants.JDK11;
	static final long JDK17 = ClassFileConstants.JDK17;
	static final long JDK21 = ClassFileConstants.JDK21;

	private ClassFileConstantsShim() {
	}
}
