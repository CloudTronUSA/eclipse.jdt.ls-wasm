package org.eclipse.jdt.ls.web.internal.teavm;

import org.teavm.model.ClassHolder;
import org.teavm.model.ClassHolderTransformer;
import org.teavm.model.ClassHolderTransformerContext;
import org.teavm.model.ElementModifier;
import org.teavm.model.FieldHolder;
import org.teavm.model.FieldReference;
import org.teavm.model.MethodDescriptor;
import org.teavm.model.MethodHolder;
import org.teavm.model.ValueType;
import org.teavm.model.emit.ProgramEmitter;
import org.teavm.model.instructions.ExitInstruction;

final class EcjMessagesTransformer implements ClassHolderTransformer {

	private static final String ECJ_MESSAGES = "org.eclipse.jdt.internal.compiler.util.Messages";
	private static final String JAVA_STRING = "java.lang.String";

	@Override
	public void transformClass(ClassHolder cls, ClassHolderTransformerContext context) {
		if (!ECJ_MESSAGES.equals(cls.getName())) {
			return;
		}
		MethodHolder initializer = cls.getMethod(new MethodDescriptor("<clinit>", ValueType.VOID));
		if (initializer == null) {
			return;
		}
		ProgramEmitter emitter = ProgramEmitter.create(initializer, context.getHierarchy());
		for (FieldHolder field : cls.getFields()) {
			if (field.hasModifier(ElementModifier.STATIC) && field.getType().isObject(JAVA_STRING)) {
				emitter.setField(new FieldReference(ECJ_MESSAGES, field.getName()), emitter.constant(label(field.getName())));
			}
		}
		emitter.addInstruction(new ExitInstruction());
	}

	private static String label(String fieldName) {
		StringBuilder label = new StringBuilder(fieldName.length());
		for (int i = 0; i < fieldName.length(); i++) {
			char c = fieldName.charAt(i);
			label.append(c == '_' ? ' ' : c);
		}
		return label.toString();
	}
}
