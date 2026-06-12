package org.eclipse.jdt.ls.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.BinaryExpression;
import org.eclipse.jdt.internal.compiler.ast.Block;
import org.eclipse.jdt.internal.compiler.ast.CharLiteral;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.OperatorIds;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.WhileStatement;

final class EcjSemanticDiagnostics {

	private static final int UNRESOLVED_NAME = 570425394;
	private static final int TYPE_MISMATCH = 16777233;
	private static final int RETURN_TYPE_MISMATCH = 16777235;
	private static final int DUPLICATE_FIELD = 33554502;
	private static final int DUPLICATE_LOCAL = 536870968;
	private static final int DUPLICATE_METHOD = 67109219;
	private static final int UNDEFINED_METHOD = 67108964;
	private static final int UNUSED_LOCAL = 536870973;
	private static final int DEAD_CODE = 536870973 + 1;
	private static final int NO_EFFECT_ASSIGNMENT = 536870974;

	private final String source;
	private final List<EcjDiagnosticsEngine.DiagnosticData> diagnostics = new ArrayList<>();
	private final Set<String> typeNames = new HashSet<>();
	private final Set<String> importedTypes = new HashSet<>();
	private final Set<String> importedOnDemandPackages = new HashSet<>();
	private final Set<String> fields = new HashSet<>();
	private final Set<String> methods = new HashSet<>();

	EcjSemanticDiagnostics(String source) {
		this(source, null);
	}

	EcjSemanticDiagnostics(String source, List<String> workspaceTypeNames) {
		this.source = source;
		typeNames.add("String");
		typeNames.add("Object");
		typeNames.add("Integer");
		typeNames.add("Long");
		typeNames.add("Boolean");
		typeNames.add("Byte");
		typeNames.add("Short");
		typeNames.add("Float");
		typeNames.add("Double");
		typeNames.add("Character");
		typeNames.add("StringBuilder");
		typeNames.add("System");
		typeNames.add("Math");
		typeNames.add("Class");
		typeNames.add("Enum");
		typeNames.add("Record");
		typeNames.add("Cloneable");
		typeNames.add("FunctionalInterface");
		typeNames.add("SafeVarargs");
		typeNames.add("SuppressWarnings");
		typeNames.add("Deprecated");
		typeNames.add("Override");
		typeNames.add("Arrays");
		typeNames.add("Collections");
		typeNames.add("Comparator");
		typeNames.add("HashMap");
		typeNames.add("LinkedList");
		typeNames.add("List");
		typeNames.add("Map");
		typeNames.add("Entry");
		typeNames.add("Objects");
		typeNames.add("Optional");
		typeNames.add("Queue");
		typeNames.add("Stream");
		typeNames.add("Collector");
		typeNames.add("Collectors");
		typeNames.add("Date");
		typeNames.add("Locale");
		typeNames.add("Scanner");
		typeNames.add("UUID");
		typeNames.add("BigDecimal");
		typeNames.add("BigInteger");
		typeNames.add("URI");
		typeNames.add("URL");
		typeNames.add("URISyntaxException");
		typeNames.add("MalformedURLException");
		typeNames.add("Callable");
		typeNames.add("CompletableFuture");
		typeNames.add("Executor");
		typeNames.add("ExecutorService");
		typeNames.add("Executors");
		typeNames.add("Future");
		typeNames.add("TimeUnit");
		typeNames.add("TemporalAccessor");
		typeNames.add("DateTimeFormatter");
		if (workspaceTypeNames != null) {
			typeNames.addAll(workspaceTypeNames);
		}
	}

	List<EcjDiagnosticsEngine.DiagnosticData> diagnose(CompilationUnitDeclaration unit) {
		if (unit.types == null) {
			return diagnostics;
		}
		for (TypeDeclaration type : unit.types) {
			collectTypeNames(type);
		}
		collectImports(unit);
		for (TypeDeclaration type : unit.types) {
			diagnoseType(type);
		}
		return diagnostics;
	}

	private void collectImports(CompilationUnitDeclaration unit) {
		importedOnDemandPackages.add("java.lang");
		if (unit.imports == null) {
			return;
		}
		for (ImportReference importReference : unit.imports) {
			if (importReference == null || importReference.tokens == null || importReference.tokens.length == 0) {
				continue;
			}
			String name = qualifiedName(importReference.tokens);
			if (importReference.trailingStarPosition >= 0) {
				importedOnDemandPackages.add(name);
			} else {
				int dot = name.lastIndexOf('.');
				importedTypes.add(dot >= 0 ? name.substring(dot + 1) : name);
			}
		}
	}

	private void collectTypeNames(TypeDeclaration type) {
		if (type == null) {
			return;
		}
		if (type.name != null) {
			typeNames.add(new String(type.name));
		}
		if (type.memberTypes != null) {
			for (TypeDeclaration member : type.memberTypes) {
				collectTypeNames(member);
			}
		}
	}

	private void diagnoseType(TypeDeclaration type) {
		fields.clear();
		methods.clear();
		Map<String, FieldDeclaration> declaredFields = new HashMap<>();
		if (type.fields != null) {
			for (FieldDeclaration field : type.fields) {
				if (field == null || field.name == null) {
					continue;
				}
				String name = new String(field.name);
				if (declaredFields.containsKey(name)) {
					addError(field, DUPLICATE_FIELD, "Duplicate field " + name);
				}
				declaredFields.put(name, field);
				fields.add(name);
				checkVariableInitialization(field, new Scope());
			}
		}
		if (type.methods != null) {
			Map<String, AbstractMethodDeclaration> declaredMethods = new HashMap<>();
			for (AbstractMethodDeclaration method : type.methods) {
				if (method == null || method.selector == null || method.isConstructor()) {
					continue;
				}
				String name = new String(method.selector);
				if (declaredMethods.containsKey(name)) {
					addError(method, DUPLICATE_METHOD, "Duplicate method " + name);
				}
				declaredMethods.put(name, method);
				methods.add(name);
			}
			for (AbstractMethodDeclaration method : type.methods) {
				diagnoseMethod(method);
			}
		}
		if (type.memberTypes != null) {
			for (TypeDeclaration member : type.memberTypes) {
				diagnoseType(member);
			}
		}
	}

	private void diagnoseMethod(AbstractMethodDeclaration method) {
		if (method == null || method.statements == null) {
			return;
		}
		Scope locals = new Scope();
		if (method.arguments != null) {
			for (AbstractVariableDeclaration argument : method.arguments) {
				if (argument != null && argument.name != null) {
					declare(argument, locals, true);
				}
			}
		}
		String returnType = "void";
		if (method instanceof org.eclipse.jdt.internal.compiler.ast.MethodDeclaration) {
			returnType = typeName(((org.eclipse.jdt.internal.compiler.ast.MethodDeclaration) method).returnType);
		}
		boolean alwaysReturns = diagnoseStatements(method.statements, locals, returnType);
		if (!"void".equals(returnType) && !alwaysReturns) {
			addError(method, RETURN_TYPE_MISMATCH, "This method must return a result of type " + returnType);
		}
		for (String name : locals.names()) {
			if (!locals.isUsed(name)) {
				addWarning(locals.node(name), UNUSED_LOCAL, "The value of the local variable " + name + " is not used");
			}
		}
	}

	private boolean diagnoseStatements(Statement[] statements, Scope locals, String returnType) {
		if (statements == null) {
			return false;
		}
		boolean unreachable = false;
		boolean alwaysReturns = false;
		for (Statement statement : statements) {
			if (statement == null) {
				continue;
			}
			if (unreachable) {
				addWarning(statement, DEAD_CODE, "Dead code");
			}
			boolean statementReturns = diagnoseStatement(statement, locals, returnType);
			if (statementReturns) {
				alwaysReturns = true;
				unreachable = true;
			} else if (!unreachable) {
				alwaysReturns = false;
			}
		}
		return alwaysReturns;
	}

	private boolean diagnoseStatement(Statement statement, Scope locals, String returnType) {
		if (statement instanceof LocalDeclaration) {
			LocalDeclaration local = (LocalDeclaration) statement;
			checkVariableInitialization(local, locals);
			declare(local, locals, false);
			return false;
		}
		if (statement instanceof ReturnStatement) {
			ReturnStatement returnStatement = (ReturnStatement) statement;
			String actual = inferType(returnStatement.expression, locals);
			if ("void".equals(returnType) && returnStatement.expression != null) {
				addError(returnStatement.expression, RETURN_TYPE_MISMATCH, "Void methods cannot return a value");
			} else if (!isAssignable(returnType, actual)) {
				addError(returnStatement.expression != null ? returnStatement.expression : returnStatement,
						RETURN_TYPE_MISMATCH,
						"Type mismatch: cannot convert from " + actual + " to " + returnType);
			}
			checkExpression(returnStatement.expression, locals);
			return true;
		}
		if (statement instanceof Block) {
			return diagnoseStatements(((Block) statement).statements, locals.child(), returnType);
		}
		if (statement instanceof IfStatement) {
			return diagnoseIf((IfStatement) statement, locals, returnType);
		}
		if (statement instanceof WhileStatement) {
			return diagnoseWhile((WhileStatement) statement, locals, returnType);
		}
		if (statement instanceof DoStatement) {
			DoStatement doStatement = (DoStatement) statement;
			checkBooleanCondition(doStatement.condition, locals);
			boolean actionReturns = diagnoseNestedStatement(doStatement.action, locals, returnType);
			checkExpression(doStatement.condition, locals);
			return isBooleanLiteral(doStatement.condition, true) && actionReturns;
		}
		if (statement instanceof ForStatement) {
			ForStatement forStatement = (ForStatement) statement;
			Scope loopScope = locals.child();
			diagnoseStatements(forStatement.initializations, loopScope, returnType);
			checkBooleanCondition(forStatement.condition, loopScope);
			checkExpression(forStatement.condition, loopScope);
			boolean actionReturns = diagnoseNestedStatement(forStatement.action, loopScope, returnType);
			diagnoseStatements(forStatement.increments, loopScope, returnType);
			return forStatement.condition == null && actionReturns;
		}
		if (statement instanceof SwitchStatement) {
			SwitchStatement switchStatement = (SwitchStatement) statement;
			checkExpression(switchStatement.expression, locals);
			diagnoseStatements(switchStatement.statements, locals.child(), returnType);
			return false;
		}
		if (statement instanceof Expression) {
			checkExpression((Expression) statement, locals);
		}
		return false;
	}

	private boolean diagnoseIf(IfStatement statement, Scope locals, String returnType) {
		checkBooleanCondition(statement.condition, locals);
		checkExpression(statement.condition, locals);
		boolean thenReturns = diagnoseNestedStatement(statement.thenStatement, locals, returnType);
		boolean elseReturns = diagnoseNestedStatement(statement.elseStatement, locals, returnType);
		return statement.thenStatement != null && statement.elseStatement != null && thenReturns && elseReturns;
	}

	private boolean diagnoseWhile(WhileStatement statement, Scope locals, String returnType) {
		checkBooleanCondition(statement.condition, locals);
		checkExpression(statement.condition, locals);
		boolean actionReturns = diagnoseNestedStatement(statement.action, locals, returnType);
		return isBooleanLiteral(statement.condition, true) && actionReturns;
	}

	private boolean diagnoseNestedStatement(Statement statement, Scope locals, String returnType) {
		if (statement == null) {
			return false;
		}
		return diagnoseStatement(statement, locals.child(), returnType);
	}

	private void declare(AbstractVariableDeclaration variable, Scope locals, boolean argument) {
		String name = new String(variable.name);
		if (locals.containsLocal(name)) {
			addError(variable, DUPLICATE_LOCAL, "Duplicate local variable " + name);
		}
		locals.put(name, typeName(variable.type), variable, argument);
	}

	private void checkVariableInitialization(AbstractVariableDeclaration variable, Scope locals) {
		if (variable == null) {
			return;
		}
		String expected = typeName(variable.type);
		String actual = inferType(variable.initialization, locals);
		if (variable.initialization != null && !isAssignable(expected, actual)) {
			addError(variable.initialization != null ? variable.initialization : variable,
					TYPE_MISMATCH,
					"Type mismatch: cannot convert from " + actual + " to " + expected);
		}
		checkExpression(variable.initialization, locals);
	}

	private void checkBooleanCondition(Expression condition, Scope locals) {
		if (condition == null) {
			return;
		}
		String actual = inferType(condition, locals);
		if (!"boolean".equals(actual) && !"unknown".equals(actual) && !"unresolved".equals(actual)) {
			addError(condition, TYPE_MISMATCH, "Type mismatch: cannot convert from " + actual + " to boolean");
		}
	}

	private void checkExpression(Expression expression, Scope locals) {
		if (expression == null) {
			return;
		}
		if (expression instanceof SingleNameReference) {
			SingleNameReference reference = (SingleNameReference) expression;
			String name = new String(reference.token);
			if (!isKnownName(name, locals)) {
				addError(reference, UNRESOLVED_NAME, name + " cannot be resolved to a variable");
			} else {
				locals.markUsed(name);
			}
			return;
		}
		if (expression instanceof QualifiedNameReference) {
			QualifiedNameReference reference = (QualifiedNameReference) expression;
			if (reference.tokens != null && reference.tokens.length > 0) {
				String first = new String(reference.tokens[0]);
				if (!isKnownName(first, locals) && !isPackageQualifiedJdkName(reference.tokens)) {
					addError(reference, UNRESOLVED_NAME, first + " cannot be resolved");
				} else {
					locals.markUsed(first);
				}
			}
			return;
		}
		if (expression instanceof Assignment) {
			Assignment assignment = (Assignment) expression;
			String expected = inferType(assignment.lhs, locals);
			String actual = inferType(assignment.expression, locals);
			if (!isAssignable(expected, actual)) {
				addError(assignment.expression, TYPE_MISMATCH,
						"Type mismatch: cannot convert from " + actual + " to " + expected);
			}
			if (sameSimpleName(assignment.lhs, assignment.expression)) {
				addWarning(assignment, NO_EFFECT_ASSIGNMENT, "The assignment to variable has no effect");
			}
			checkExpression(assignment.lhs, locals);
			checkExpression(assignment.expression, locals);
			return;
		}
		if (expression instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) expression;
			checkExpression(binary.left, locals);
			checkExpression(binary.right, locals);
			return;
		}
		if (expression instanceof AllocationExpression) {
			AllocationExpression allocation = (AllocationExpression) expression;
			if (allocation.arguments != null) {
				for (Expression argument : allocation.arguments) {
					checkExpression(argument, locals);
				}
			}
			return;
		}
		if (expression instanceof MessageSend) {
			MessageSend send = (MessageSend) expression;
			if (send.receiver == null || send.receiver instanceof ThisReference && send.receiver.isImplicitThis()) {
				String name = new String(send.selector);
				if (!methods.contains(name) && !"println".equals(name)) {
					addError(send, UNDEFINED_METHOD, "The method " + name + "() is undefined");
				}
			} else if (!isTypeReceiver(send.receiver)) {
				checkExpression(send.receiver, locals);
			}
			if (send.arguments != null) {
				for (Expression argument : send.arguments) {
					checkExpression(argument, locals);
				}
			}
		}
	}

	private boolean isKnownName(String name, Scope locals) {
		return locals.contains(name) || fields.contains(name) || isKnownTypeName(name)
				|| "this".equals(name) || "super".equals(name);
	}

	private boolean isKnownTypeName(String name) {
		if (typeNames.contains(name) || importedTypes.contains(name)) {
			return true;
		}
		for (String packageName : importedOnDemandPackages) {
			if (jdkResourceExists(packageName.replace('.', '/') + "/" + name + ".class")) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPackageQualifiedJdkName(char[][] tokens) {
		if (tokens == null || tokens.length < 2) {
			return false;
		}
		String first = new String(tokens[0]);
		if ("java".equals(first) || "javax".equals(first) || "jdk".equals(first) || "sun".equals(first)) {
			return true;
		}
		return tokens.length >= 3
				&& "com".equals(first)
				&& "sun".equals(new String(tokens[1]))
				|| tokens.length >= 2
				&& "org".equals(first)
				&& ("w3c".equals(new String(tokens[1])) || "xml".equals(new String(tokens[1])));
	}

	private boolean isTypeReceiver(Expression expression) {
		if (expression instanceof SingleNameReference) {
			return isKnownTypeName(new String(((SingleNameReference) expression).token));
		}
		if (expression instanceof QualifiedNameReference) {
			return isPackageQualifiedJdkName(((QualifiedNameReference) expression).tokens);
		}
		return false;
	}

	private String inferType(Expression expression, Scope locals) {
		if (expression == null) {
			return "void";
		}
		if (expression instanceof IntLiteral) {
			return "int";
		}
		if (expression instanceof LongLiteral) {
			return "long";
		}
		if (expression instanceof FloatLiteral) {
			return "float";
		}
		if (expression instanceof DoubleLiteral) {
			return "double";
		}
		if (expression instanceof CharLiteral) {
			return "char";
		}
		if (expression instanceof StringLiteral) {
			return "String";
		}
		if (expression instanceof TrueLiteral || expression instanceof FalseLiteral) {
			return "boolean";
		}
		if (expression instanceof NullLiteral) {
			return "null";
		}
		if (expression instanceof SingleNameReference) {
			String name = new String(((SingleNameReference) expression).token);
			String type = locals.get(name);
			return type != null ? type : fields.contains(name) ? "unknown" : "unresolved";
		}
		if (expression instanceof QualifiedNameReference) {
			QualifiedNameReference reference = (QualifiedNameReference) expression;
			if (reference.tokens != null && reference.tokens.length > 0) {
				String first = new String(reference.tokens[0]);
				if (!isKnownName(first, locals) && !isPackageQualifiedJdkName(reference.tokens)) {
					return "unresolved";
				}
			}
			return "unknown";
		}
		if (expression instanceof Assignment) {
			return inferType(((Assignment) expression).lhs, locals);
		}
		if (expression instanceof AllocationExpression) {
			return typeName(((AllocationExpression) expression).type);
		}
		if (expression instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) expression;
			int operator = (binary.bits & ASTNode.OperatorMASK) >> ASTNode.OperatorSHIFT;
			if (operator == OperatorIds.EQUAL_EQUAL || operator == OperatorIds.NOT_EQUAL
					|| operator == OperatorIds.LESS || operator == OperatorIds.LESS_EQUAL
					|| operator == OperatorIds.GREATER || operator == OperatorIds.GREATER_EQUAL
					|| operator == OperatorIds.AND_AND || operator == OperatorIds.OR_OR) {
				return "boolean";
			}
			String left = inferType(binary.left, locals);
			String right = inferType(binary.right, locals);
			if ("String".equals(left) || "String".equals(right)) {
				return "String";
			}
			if ("double".equals(left) || "double".equals(right)) {
				return "double";
			}
			if ("float".equals(left) || "float".equals(right)) {
				return "float";
			}
			if ("long".equals(left) || "long".equals(right)) {
				return "long";
			}
			if ("int".equals(left) && "int".equals(right)) {
				return "int";
			}
			return "unknown";
		}
		return "unknown";
	}

	private static String typeName(TypeReference type) {
		if (type == null) {
			return "void";
		}
		StringBuilder printed = type.print(0, new StringBuilder());
		return printed.toString();
	}

	private static boolean isAssignable(String expected, String actual) {
		if (expected == null || actual == null || "unknown".equals(expected) || "unknown".equals(actual)) {
			return true;
		}
		if ("unresolved".equals(actual)) {
			return true;
		}
		if (expected.equals(actual)) {
			return true;
		}
		if ("Object".equals(expected) && !isPrimitive(actual)) {
			return true;
		}
		if ("null".equals(actual)) {
			return !isPrimitive(expected);
		}
		return "long".equals(expected) && "int".equals(actual)
				|| "float".equals(expected) && ("int".equals(actual) || "long".equals(actual))
				|| "double".equals(expected) && ("int".equals(actual) || "long".equals(actual) || "float".equals(actual));
	}

	private static boolean isPrimitive(String type) {
		return "int".equals(type) || "long".equals(type) || "boolean".equals(type) || "char".equals(type)
				|| "byte".equals(type) || "short".equals(type) || "float".equals(type) || "double".equals(type);
	}

	private static boolean sameSimpleName(Expression left, Expression right) {
		if (left instanceof SingleNameReference && right instanceof SingleNameReference) {
			return new String(((SingleNameReference) left).token).equals(new String(((SingleNameReference) right).token));
		}
		return false;
	}

	private static boolean isBooleanLiteral(Expression expression, boolean value) {
		return value ? expression instanceof TrueLiteral : expression instanceof FalseLiteral;
	}

	private static boolean jdkResourceExists(String resourceName) {
		InputStream input = EcjSemanticDiagnostics.class.getClassLoader().getResourceAsStream(resourceName);
		if (input == null) {
			return false;
		}
		try {
			input.close();
		} catch (IOException ignored) {
		}
		return true;
	}

	private static String qualifiedName(char[][] parts) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append('.');
			}
			result.append(parts[i]);
		}
		return result.toString();
	}

	private void addError(ASTNode node, int code, String message) {
		add(node, code, 1, message);
	}

	private void addWarning(ASTNode node, int code, String message) {
		add(node, code, 2, message);
	}

	private void add(ASTNode node, int code, int severity, String message) {
		if (node == null) {
			return;
		}
		int start = Math.max(0, node.sourceStart);
		int end = Math.max(start, node.sourceEnd + 1);
		EcjDiagnosticsEngine.Position startPosition = position(start);
		EcjDiagnosticsEngine.Position endPosition = position(end);
		diagnostics.add(new EcjDiagnosticsEngine.DiagnosticData(
				startPosition.line,
				startPosition.character,
				endPosition.line,
				endPosition.character,
				severity,
				code,
				message));
	}

	private EcjDiagnosticsEngine.Position position(int offset) {
		int line = 0;
		int character = 0;
		int max = Math.min(offset, source.length());
		for (int i = 0; i < max; i++) {
			char c = source.charAt(i);
			if (c == '\n') {
				line++;
				character = 0;
			} else {
				character++;
			}
		}
		return new EcjDiagnosticsEngine.Position(line, character);
	}

	private static final class Scope {
		private final Scope parent;
		private final Map<String, LocalInfo> locals = new HashMap<>();

		Scope() {
			this(null);
		}

		private Scope(Scope parent) {
			this.parent = parent;
		}

		Scope child() {
			return new Scope(this);
		}

		boolean containsLocal(String name) {
			return locals.containsKey(name);
		}

		boolean contains(String name) {
			return locals.containsKey(name) || parent != null && parent.contains(name);
		}

		String get(String name) {
			LocalInfo info = locals.get(name);
			if (info != null) {
				return info.type;
			}
			return parent == null ? null : parent.get(name);
		}

		void put(String name, String type, ASTNode node, boolean argument) {
			locals.put(name, new LocalInfo(type, node, argument));
		}

		void markUsed(String name) {
			LocalInfo info = locals.get(name);
			if (info != null) {
				info.used = true;
			} else if (parent != null) {
				parent.markUsed(name);
			}
		}

		boolean isUsed(String name) {
			LocalInfo info = locals.get(name);
			return info == null || info.used;
		}

		ASTNode node(String name) {
			LocalInfo info = locals.get(name);
			return info == null ? null : info.node;
		}

		Set<String> names() {
			return locals.keySet();
		}
	}

	private static final class LocalInfo {
		final String type;
		final ASTNode node;
		boolean used;

		LocalInfo(String type, ASTNode node, boolean argument) {
			this.type = type;
			this.node = node;
			this.used = argument;
		}
	}
}
