package org.eclipse.jdt.ls.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFormatException;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;

final class EcjCompilerDiagnostics {

	List<CategorizedProblem> diagnose(MemoryCompilationUnit unit) {
		return diagnose(unit, Collections.emptyMap(), WebCompilerConfiguration.DEFAULT);
	}

	List<CategorizedProblem> diagnose(MemoryCompilationUnit unit, Map<String, MemoryCompilationUnit> workspaceUnits) {
		return diagnose(unit, workspaceUnits, WebCompilerConfiguration.DEFAULT);
	}

	List<CategorizedProblem> diagnose(MemoryCompilationUnit unit, Map<String, MemoryCompilationUnit> workspaceUnits,
			WebCompilerConfiguration configuration) {
		CollectingRequestor requestor = new CollectingRequestor(new String(unit.getFileName()));
		NoCodegenCompiler compiler = new NoCodegenCompiler(new WebNameEnvironment(unit, workspaceUnits),
				configuration.compilerOptions(), requestor);
		compiler.compile(new ICompilationUnit[] { unit });
		return requestor.problems();
	}

	private static final class NoCodegenCompiler extends Compiler {
		NoCodegenCompiler(INameEnvironment environment, CompilerOptions options, ICompilerRequestor requestor) {
			super(environment, DefaultErrorHandlingPolicies.proceedWithAllProblems(), options, requestor,
					new EcjDiagnosticsEngine.WebProblemFactory());
		}

		@Override
		public void process(CompilationUnitDeclaration unit, int index) {
			this.lookupEnvironment.unitBeingCompleted = unit;
			this.parser.getMethodBodies(unit);
			if (unit.scope != null) {
				unit.scope.faultInTypes();
				unit.scope.verifyMethods(this.lookupEnvironment.methodVerifier());
			}
			unit.resolve();
			if (!this.options.ignoreMethodBodies) {
				unit.analyseCode();
			}
			if (this.options.produceReferenceInfo && unit.scope != null) {
				unit.scope.storeDependencyInfo();
			}
			unit.finalizeProblems();
			unit.compilationResult.totalUnitsKnown = this.totalUnits;
			this.lookupEnvironment.unitBeingCompleted = null;
		}
	}

	private static final class CollectingRequestor implements ICompilerRequestor {
		private final String userFileName;
		private final List<CategorizedProblem> problems = new ArrayList<>();

		CollectingRequestor(String userFileName) {
			this.userFileName = userFileName;
		}

		@Override
		public void acceptResult(CompilationResult result) {
			if (!userFileName.equals(new String(result.getFileName()))) {
				return;
			}
			CategorizedProblem[] reported = result.getAllProblems();
			if (reported != null) {
				problems.addAll(Arrays.asList(reported));
			}
		}

		List<CategorizedProblem> problems() {
			problems.sort(Comparator.comparingInt(CategorizedProblem::getSourceStart));
			return problems;
		}
	}

	private static final class WebNameEnvironment implements INameEnvironment {
		private final Map<String, MemoryCompilationUnit> sourceTypes = new HashMap<>();
		private final Map<String, Boolean> packages = new HashMap<>();

		WebNameEnvironment(MemoryCompilationUnit userUnit, Map<String, MemoryCompilationUnit> workspaceUnits) {
			registerPackage("");
			for (MemoryCompilationUnit unit : workspaceUnits.values()) {
				register(unit);
			}
			register(userUnit);
			registerPackage("processing");
			registerPackage("processing.awt");
			registerPackage("processing.core");
			registerPackage("processing.data");
			registerPackage("processing.event");
			registerPackage("processing.opengl");
			registerPackage("java");
			registerPackage("java.lang");
			registerJdkSignaturePackages();
			registerJavaLang("Object", "package java.lang; public class Object {}");
			registerJavaLang("CharSequence",
					"package java.lang; public interface CharSequence { int length(); char charAt(int index); String toString(); }");
			registerJavaLang("String",
					"package java.lang; public final class String implements CharSequence, Comparable<String> { public int length() { return 0; } public char charAt(int index) { return 0; } public boolean isEmpty() { return false; } public String trim() { return this; } public String substring(int beginIndex) { return this; } public String substring(int beginIndex, int endIndex) { return this; } public boolean contains(CharSequence value) { return false; } public boolean startsWith(String prefix) { return false; } public boolean endsWith(String suffix) { return false; } public int indexOf(String value) { return 0; } public int indexOf(String value, int fromIndex) { return 0; } public String replace(char oldChar, char newChar) { return this; } public String replace(CharSequence target, CharSequence replacement) { return this; } public String replaceAll(String regex, String replacement) { return this; } public String replaceFirst(String regex, String replacement) { return this; } public String toLowerCase() { return this; } public String toUpperCase() { return this; } public int compareTo(String other) { return 0; } public static String valueOf(Object value) { return null; } public static String valueOf(int value) { return null; } public static String valueOf(long value) { return null; } public static String valueOf(float value) { return null; } public static String valueOf(double value) { return null; } public static String valueOf(boolean value) { return null; } public static String valueOf(char value) { return null; } public static String format(String format, Object... args) { return null; } public static String join(CharSequence delimiter, java.lang.Iterable<? extends CharSequence> elements) { return null; } }");
			registerJavaLang("StringBuilder",
					"package java.lang; public final class StringBuilder implements CharSequence { public StringBuilder() {} public StringBuilder(String value) {} public StringBuilder append(Object value) { return this; } public StringBuilder append(String value) { return this; } public StringBuilder append(int value) { return this; } public StringBuilder append(long value) { return this; } public StringBuilder append(boolean value) { return this; } public int length() { return 0; } public char charAt(int index) { return 0; } public String substring(int start) { return null; } public String toString() { return null; } }");
			registerJavaLang("Integer",
					"package java.lang; public final class Integer extends Number implements Comparable<Integer> { public static final int MAX_VALUE = 0; public static final int MIN_VALUE = 0; public Integer(int value) {} public int intValue() { return 0; } public static int parseInt(String value) { return 0; } public static Integer valueOf(int value) { return null; } public int compareTo(Integer other) { return 0; } }");
			registerJavaLang("Long",
					"package java.lang; public final class Long extends Number implements Comparable<Long> { public Long(long value) {} public int intValue() { return 0; } public long longValue() { return 0; } public static long parseLong(String value) { return 0; } public static Long valueOf(long value) { return null; } public int compareTo(Long other) { return 0; } }");
			registerJavaLang("Boolean",
					"package java.lang; public final class Boolean implements Comparable<Boolean> { public static final Boolean TRUE = null; public static final Boolean FALSE = null; public Boolean(boolean value) {} public boolean booleanValue() { return false; } public static Boolean valueOf(boolean value) { return null; } public int compareTo(Boolean other) { return 0; } }");
			registerJavaLang("Byte",
					"package java.lang; public final class Byte extends Number implements Comparable<Byte> { public static final byte MAX_VALUE = 0; public static final byte MIN_VALUE = 0; public Byte(byte value) {} public byte byteValue() { return 0; } public int intValue() { return 0; } public long longValue() { return 0; } public static byte parseByte(String value) { return 0; } public static Byte valueOf(byte value) { return null; } public int compareTo(Byte other) { return 0; } }");
			registerJavaLang("Short",
					"package java.lang; public final class Short extends Number implements Comparable<Short> { public static final short MAX_VALUE = 0; public static final short MIN_VALUE = 0; public Short(short value) {} public short shortValue() { return 0; } public int intValue() { return 0; } public long longValue() { return 0; } public static short parseShort(String value) { return 0; } public static Short valueOf(short value) { return null; } public int compareTo(Short other) { return 0; } }");
			registerJavaLang("Float",
					"package java.lang; public final class Float extends Number implements Comparable<Float> { public static final float MAX_VALUE = 0; public static final float MIN_VALUE = 0; public Float(float value) {} public int intValue() { return 0; } public long longValue() { return 0; } public float floatValue() { return 0; } public static float parseFloat(String value) { return 0; } public static Float valueOf(float value) { return null; } public int compareTo(Float other) { return 0; } }");
			registerJavaLang("Double",
					"package java.lang; public final class Double extends Number implements Comparable<Double> { public static final double MAX_VALUE = 0; public static final double MIN_VALUE = 0; public Double(double value) {} public int intValue() { return 0; } public long longValue() { return 0; } public double doubleValue() { return 0; } public static double parseDouble(String value) { return 0; } public static Double valueOf(double value) { return null; } public int compareTo(Double other) { return 0; } }");
			registerJavaLang("Character",
					"package java.lang; public final class Character implements Comparable<Character> { public static final char MAX_VALUE = 0; public static final char MIN_VALUE = 0; public Character(char value) {} public char charValue() { return 0; } public static Character valueOf(char value) { return null; } public static boolean isWhitespace(char value) { return false; } public static boolean isJavaIdentifierStart(char value) { return false; } public static boolean isJavaIdentifierPart(char value) { return false; } public int compareTo(Character other) { return 0; } }");
			registerJavaLang("Number",
					"package java.lang; public abstract class Number { public int intValue() { return 0; } public long longValue() { return 0; } public double doubleValue() { return 0; } public float floatValue() { return 0; } }");
			registerJavaLang("Comparable", "package java.lang; public interface Comparable<T> { int compareTo(T other); }");
			registerJavaLang("Iterable", "package java.lang; public interface Iterable<T> { java.util.Iterator<T> iterator(); }");
			registerJavaLang("Runnable", "package java.lang; public interface Runnable { void run(); }");
			registerJavaLang("AutoCloseable", "package java.lang; public interface AutoCloseable { void close(); }");
			registerJavaLang("Cloneable", "package java.lang; public interface Cloneable {}");
			registerJavaLang("Class", "package java.lang; public final class Class<T> {}");
			registerJavaLang("Void", "package java.lang; public final class Void {}");
			registerJavaLang("Math",
					"package java.lang; public final class Math { public static final double PI = 0; public static int abs(int value) { return 0; } public static long abs(long value) { return 0; } public static float abs(float value) { return 0; } public static double abs(double value) { return 0; } public static int max(int a, int b) { return 0; } public static long max(long a, long b) { return 0; } public static float max(float a, float b) { return 0; } public static double max(double a, double b) { return 0; } public static int min(int a, int b) { return 0; } public static long min(long a, long b) { return 0; } public static float min(float a, float b) { return 0; } public static double min(double a, double b) { return 0; } public static double sqrt(double value) { return 0; } public static double pow(double a, double b) { return 0; } public static double round(double value) { return 0; } }");
			registerJavaLang("Enum",
					"package java.lang; public abstract class Enum<E extends Enum<E>> implements Comparable<E>, java.io.Serializable { protected Enum(String name, int ordinal) {} public final String name() { return null; } public final int ordinal() { return 0; } public String toString() { return name(); } public final int compareTo(E other) { return 0; } public final Class<E> getDeclaringClass() { return null; } public static <T extends Enum<T>> T valueOf(Class<T> enumType, String name) { return null; } }");
			registerJavaLang("Record",
					"package java.lang; public abstract class Record { public abstract boolean equals(Object other); public abstract int hashCode(); public abstract String toString(); }");
			registerJavaLang("Override", "package java.lang; public @interface Override {}");
			registerJavaLang("Deprecated", "package java.lang; public @interface Deprecated {}");
			registerJavaLang("FunctionalInterface", "package java.lang; public @interface FunctionalInterface {}");
			registerJavaLang("SafeVarargs", "package java.lang; public @interface SafeVarargs {}");
			registerJavaLang("SuppressWarnings", "package java.lang; public @interface SuppressWarnings { String[] value(); }");
			registerJavaLang("Throwable",
					"package java.lang; public class Throwable { public Throwable() {} public Throwable(String message) {} public Throwable(String message, Throwable cause) {} public String getMessage() { return null; } public Throwable getCause() { return null; } public void printStackTrace() {} }");
			registerJavaLang("Exception",
					"package java.lang; public class Exception extends Throwable { public Exception() {} public Exception(String message) {} public Exception(String message, Throwable cause) {} }");
			registerJavaLang("RuntimeException",
					"package java.lang; public class RuntimeException extends Exception { public RuntimeException() {} public RuntimeException(String message) {} public RuntimeException(String message, Throwable cause) {} }");
			registerJavaLang("IllegalArgumentException", "package java.lang; public class IllegalArgumentException extends RuntimeException { public IllegalArgumentException() {} public IllegalArgumentException(String message) {} }");
			registerJavaLang("IllegalStateException", "package java.lang; public class IllegalStateException extends RuntimeException { public IllegalStateException() {} public IllegalStateException(String message) {} }");
			registerJavaLang("NullPointerException", "package java.lang; public class NullPointerException extends RuntimeException { public NullPointerException() {} public NullPointerException(String message) {} }");
			registerJavaLang("IndexOutOfBoundsException", "package java.lang; public class IndexOutOfBoundsException extends RuntimeException { public IndexOutOfBoundsException() {} public IndexOutOfBoundsException(String message) {} }");
			registerJavaLang("NumberFormatException", "package java.lang; public class NumberFormatException extends IllegalArgumentException { public NumberFormatException() {} public NumberFormatException(String message) {} }");
			registerJavaLang("UnsupportedOperationException", "package java.lang; public class UnsupportedOperationException extends RuntimeException { public UnsupportedOperationException() {} public UnsupportedOperationException(String message) {} }");
			registerJavaLang("ClassCastException", "package java.lang; public class ClassCastException extends RuntimeException { public ClassCastException() {} public ClassCastException(String message) {} }");
			registerJavaLang("ArithmeticException", "package java.lang; public class ArithmeticException extends RuntimeException { public ArithmeticException() {} public ArithmeticException(String message) {} }");
			registerJavaLang("Error",
					"package java.lang; public class Error extends Throwable { public Error() {} public Error(String message) {} public Error(String message, Throwable cause) {} }");
			registerJavaLang("AssertionError", "package java.lang; public class AssertionError extends Error { public AssertionError() {} public AssertionError(Object detailMessage) {} }");
			registerJavaLang("System",
					"package java.lang; public final class System { public static final java.io.PrintStream out = null; }");
			register("java.io.PrintStream",
					"package java.io; public class PrintStream { public void println(Object value) {} public void println(String value) {} public void println(int value) {} public void println(long value) {} public void println(boolean value) {} }");
			register("java.io.Serializable", "package java.io; public interface Serializable {}");
			register("java.io.Closeable",
					"package java.io; public interface Closeable extends java.lang.AutoCloseable { void close() throws IOException; }");
			register("java.io.Flushable", "package java.io; public interface Flushable { void flush() throws IOException; }");
			register("java.io.IOException",
					"package java.io; public class IOException extends java.lang.Exception { public IOException() {} public IOException(String message) {} }");
			register("java.io.File",
					"package java.io; public class File { public File(String pathname) {} public String getName() { return null; } public String getPath() { return null; } public boolean exists() { return false; } public boolean isFile() { return false; } public boolean isDirectory() { return false; } public long length() { return 0; } public java.nio.file.Path toPath() { return null; } }");
			register("java.io.InputStream",
					"package java.io; public abstract class InputStream implements Closeable { public abstract int read() throws IOException; public int read(byte[] value) throws IOException { return 0; } public void close() throws IOException {} }");
			register("java.io.OutputStream",
					"package java.io; public abstract class OutputStream implements Closeable, Flushable { public abstract void write(int value) throws IOException; public void write(byte[] value) throws IOException {} public void flush() throws IOException {} public void close() throws IOException {} }");
			register("java.io.Reader",
					"package java.io; public abstract class Reader implements Closeable { public int read(char[] value) throws IOException { return 0; } public void close() throws IOException {} }");
			register("java.io.Writer",
					"package java.io; public abstract class Writer implements Closeable, Flushable { public void write(String value) throws IOException {} public void flush() throws IOException {} public void close() throws IOException {} }");
			register("java.util.Iterator",
					"package java.util; public interface Iterator<E> { boolean hasNext(); E next(); void remove(); }");
			register("java.util.Collection",
					"package java.util; public interface Collection<E> extends java.lang.Iterable<E> { int size(); boolean isEmpty(); boolean contains(Object value); boolean containsAll(Collection<?> values); boolean add(E value); boolean addAll(Collection<? extends E> values); boolean remove(Object value); void clear(); Iterator<E> iterator(); Object[] toArray(); java.util.stream.Stream<E> stream(); }");
			register("java.util.List",
					"package java.util; public interface List<E> extends Collection<E> { E get(int index); E set(int index, E value); void add(int index, E value); boolean add(E value); boolean addAll(Collection<? extends E> values); E remove(int index); int indexOf(Object value); void sort(Comparator<? super E> comparator); static <E> List<E> of() { return null; } static <E> List<E> of(E value) { return null; } static <E> List<E> of(E first, E second) { return null; } static <E> List<E> of(E... values) { return null; } }");
			register("java.util.ArrayList",
					"package java.util; public class ArrayList<E> implements List<E> { public ArrayList() {} public ArrayList(int initialCapacity) {} public ArrayList(Collection<? extends E> values) {} public E get(int index) { return null; } public E set(int index, E value) { return null; } public void add(int index, E value) {} public boolean add(E value) { return false; } public boolean addAll(Collection<? extends E> values) { return false; } public E remove(int index) { return null; } public boolean remove(Object value) { return false; } public int indexOf(Object value) { return 0; } public boolean contains(Object value) { return false; } public boolean containsAll(Collection<?> values) { return false; } public int size() { return 0; } public boolean isEmpty() { return false; } public void clear() {} public void sort(Comparator<? super E> comparator) {} public Iterator<E> iterator() { return null; } public Object[] toArray() { return null; } public java.util.stream.Stream<E> stream() { return null; } }");
			register("java.util.Set",
					"package java.util; public interface Set<E> extends Collection<E> { boolean add(E value); static <E> Set<E> of() { return null; } static <E> Set<E> of(E value) { return null; } static <E> Set<E> of(E first, E second) { return null; } static <E> Set<E> of(E... values) { return null; } }");
			register("java.util.HashSet",
					"package java.util; public class HashSet<E> implements Set<E> { public HashSet() {} public HashSet(Collection<? extends E> values) {} public boolean add(E value) { return false; } public boolean addAll(Collection<? extends E> values) { return false; } public boolean remove(Object value) { return false; } public boolean contains(Object value) { return false; } public boolean containsAll(Collection<?> values) { return false; } public int size() { return 0; } public boolean isEmpty() { return false; } public void clear() {} public Iterator<E> iterator() { return null; } public Object[] toArray() { return null; } public java.util.stream.Stream<E> stream() { return null; } }");
			register("java.util.Map",
					"package java.util; public interface Map<K,V> { V get(Object key); V put(K key, V value); V remove(Object key); boolean containsKey(Object key); boolean containsValue(Object value); int size(); boolean isEmpty(); void clear(); Set<K> keySet(); Collection<V> values(); Set<Entry<K,V>> entrySet(); public interface Entry<K,V> { K getKey(); V getValue(); } static <K,V> Map<K,V> of() { return null; } static <K,V> Map<K,V> of(K key, V value) { return null; } static <K,V> Map<K,V> of(K k1, V v1, K k2, V v2) { return null; } }");
			register("java.util.HashMap",
					"package java.util; public class HashMap<K,V> implements Map<K,V> { public HashMap() {} public V get(Object key) { return null; } public V put(K key, V value) { return null; } public V remove(Object key) { return null; } public boolean containsKey(Object key) { return false; } public boolean containsValue(Object value) { return false; } public int size() { return 0; } public boolean isEmpty() { return false; } public void clear() {} public Set<K> keySet() { return null; } public Collection<V> values() { return null; } public Set<Map.Entry<K,V>> entrySet() { return null; } }");
			register("java.util.Queue",
					"package java.util; public interface Queue<E> extends Collection<E> { boolean offer(E value); E poll(); E peek(); }");
			register("java.util.Deque",
					"package java.util; public interface Deque<E> extends Queue<E> { void addFirst(E value); void addLast(E value); E removeFirst(); E removeLast(); E getFirst(); E getLast(); }");
			register("java.util.LinkedList",
					"package java.util; public class LinkedList<E> implements List<E>, Deque<E> { public LinkedList() {} public LinkedList(Collection<? extends E> values) {} public E get(int index) { return null; } public E set(int index, E value) { return null; } public void add(int index, E value) {} public boolean add(E value) { return false; } public boolean addAll(Collection<? extends E> values) { return false; } public E remove(int index) { return null; } public boolean remove(Object value) { return false; } public int indexOf(Object value) { return 0; } public boolean contains(Object value) { return false; } public boolean containsAll(Collection<?> values) { return false; } public int size() { return 0; } public boolean isEmpty() { return false; } public void clear() {} public void sort(Comparator<? super E> comparator) {} public Iterator<E> iterator() { return null; } public Object[] toArray() { return null; } public java.util.stream.Stream<E> stream() { return null; } public boolean offer(E value) { return false; } public E poll() { return null; } public E peek() { return null; } public void addFirst(E value) {} public void addLast(E value) {} public E removeFirst() { return null; } public E removeLast() { return null; } public E getFirst() { return null; } public E getLast() { return null; } }");
			register("java.util.Optional",
					"package java.util; public final class Optional<T> { public static <T> Optional<T> empty() { return null; } public static <T> Optional<T> of(T value) { return null; } public static <T> Optional<T> ofNullable(T value) { return null; } public boolean isPresent() { return false; } public boolean isEmpty() { return false; } public T get() { return null; } public T orElse(T other) { return null; } public T orElseGet(java.util.function.Supplier<? extends T> supplier) { return null; } public <U> Optional<U> map(java.util.function.Function<? super T, ? extends U> mapper) { return null; } public Optional<T> filter(java.util.function.Predicate<? super T> predicate) { return null; } public void ifPresent(java.util.function.Consumer<? super T> action) {} }");
			register("java.util.Arrays",
					"package java.util; public final class Arrays { public static <T> List<T> asList(T... values) { return null; } public static String toString(Object[] values) { return null; } public static int[] copyOf(int[] original, int newLength) { return null; } public static <T> T[] copyOf(T[] original, int newLength) { return null; } }");
			register("java.util.Objects",
					"package java.util; public final class Objects { public static boolean equals(Object a, Object b) { return false; } public static <T> T requireNonNull(T value) { return null; } public static <T> T requireNonNull(T value, String message) { return null; } public static String toString(Object value) { return null; } }");
			register("java.util.Comparator",
					"package java.util; public interface Comparator<T> { int compare(T first, T second); static <T extends java.lang.Comparable<? super T>> Comparator<T> naturalOrder() { return null; } }");
			register("java.util.Collections",
					"package java.util; public final class Collections { public static <T> java.util.List<T> emptyList() { return null; } public static <T> java.util.Set<T> emptySet() { return null; } public static <K,V> java.util.Map<K,V> emptyMap() { return null; } public static <T> java.util.List<T> singletonList(T value) { return null; } public static <T> void sort(java.util.List<T> values) {} public static void sort(java.util.List values, Comparator comparator) {} }");
			register("java.util.regex.Pattern",
					"package java.util.regex; public final class Pattern { public static Pattern compile(String regex) { return null; } public Matcher matcher(CharSequence input) { return null; } public String[] split(CharSequence input) { return null; } }");
			register("java.util.regex.Matcher",
					"package java.util.regex; public final class Matcher { public boolean matches() { return false; } public boolean find() { return false; } public String group() { return null; } public String group(int group) { return null; } }");
			register("java.util.Date",
					"package java.util; public class Date implements java.lang.Comparable<Date>, java.io.Serializable { public Date() {} public Date(long date) {} public long getTime() { return 0; } public boolean before(Date when) { return false; } public boolean after(Date when) { return false; } public java.time.Instant toInstant() { return null; } public int compareTo(Date other) { return 0; } }");
			register("java.util.Locale",
					"package java.util; public final class Locale implements java.io.Serializable { public static final Locale US = null; public static final Locale UK = null; public static final Locale ROOT = null; public Locale(String language) {} public Locale(String language, String country) {} public String getLanguage() { return null; } public String getCountry() { return null; } public String toLanguageTag() { return null; } public static Locale forLanguageTag(String languageTag) { return null; } }");
			register("java.util.Scanner",
					"package java.util; public final class Scanner implements java.lang.AutoCloseable { public Scanner(String source) {} public boolean hasNext() { return false; } public String next() { return null; } public int nextInt() { return 0; } public long nextLong() { return 0; } public Scanner useLocale(Locale locale) { return this; } public void close() {} }");
			register("java.util.UUID",
					"package java.util; public final class UUID implements java.lang.Comparable<UUID>, java.io.Serializable { public static UUID randomUUID() { return null; } public static UUID fromString(String name) { return null; } public long getMostSignificantBits() { return 0; } public long getLeastSignificantBits() { return 0; } public String toString() { return null; } public int compareTo(UUID other) { return 0; } }");
			register("java.math.BigDecimal",
					"package java.math; public class BigDecimal extends java.lang.Number implements java.lang.Comparable<BigDecimal> { public static final BigDecimal ZERO = null; public static final BigDecimal ONE = null; public BigDecimal(String value) {} public BigDecimal(int value) {} public BigDecimal(long value) {} public static BigDecimal valueOf(long value) { return null; } public BigDecimal add(BigDecimal augend) { return null; } public BigDecimal subtract(BigDecimal subtrahend) { return null; } public BigDecimal multiply(BigDecimal multiplicand) { return null; } public BigDecimal divide(BigDecimal divisor) { return null; } public int intValue() { return 0; } public long longValue() { return 0; } public double doubleValue() { return 0; } public int compareTo(BigDecimal other) { return 0; } }");
			register("java.math.BigInteger",
					"package java.math; public class BigInteger extends java.lang.Number implements java.lang.Comparable<BigInteger> { public static final BigInteger ZERO = null; public static final BigInteger ONE = null; public BigInteger(String value) {} public static BigInteger valueOf(long value) { return null; } public BigInteger add(BigInteger value) { return null; } public BigInteger subtract(BigInteger value) { return null; } public BigInteger multiply(BigInteger value) { return null; } public int intValue() { return 0; } public long longValue() { return 0; } public double doubleValue() { return 0; } public int compareTo(BigInteger other) { return 0; } }");
			register("java.net.URI",
					"package java.net; public final class URI implements java.lang.Comparable<URI>, java.io.Serializable { public URI(String str) throws URISyntaxException {} public static URI create(String str) { return null; } public String getScheme() { return null; } public String getHost() { return null; } public String getPath() { return null; } public URL toURL() throws java.net.MalformedURLException { return null; } public String toString() { return null; } public int compareTo(URI other) { return 0; } }");
			register("java.net.URL",
					"package java.net; public class URL implements java.io.Serializable { public URL(String spec) throws MalformedURLException {} public String getProtocol() { return null; } public String getHost() { return null; } public String getPath() { return null; } public URI toURI() throws URISyntaxException { return null; } public String toString() { return null; } }");
			register("java.net.URISyntaxException",
					"package java.net; public class URISyntaxException extends java.lang.Exception { public URISyntaxException(String input, String reason) {} public String getInput() { return null; } public String getReason() { return null; } }");
			register("java.net.MalformedURLException",
					"package java.net; public class MalformedURLException extends java.io.IOException { public MalformedURLException() {} public MalformedURLException(String message) {} }");
			register("java.lang.annotation.Annotation",
					"package java.lang.annotation; public interface Annotation {}");
			register("java.lang.annotation.Retention",
					"package java.lang.annotation; public @interface Retention { RetentionPolicy value(); }");
			register("java.lang.annotation.RetentionPolicy",
					"package java.lang.annotation; public enum RetentionPolicy { SOURCE, CLASS, RUNTIME }");
			register("java.lang.annotation.Target",
					"package java.lang.annotation; public @interface Target { ElementType[] value(); }");
			register("java.lang.annotation.ElementType",
					"package java.lang.annotation; public enum ElementType { TYPE, FIELD, METHOD, PARAMETER, CONSTRUCTOR, LOCAL_VARIABLE, ANNOTATION_TYPE, PACKAGE, TYPE_PARAMETER, TYPE_USE, MODULE, RECORD_COMPONENT }");
			register("java.lang.annotation.Documented",
					"package java.lang.annotation; public @interface Documented {}");
			register("java.lang.annotation.Inherited",
					"package java.lang.annotation; public @interface Inherited {}");
			register("java.lang.annotation.Repeatable",
					"package java.lang.annotation; public @interface Repeatable { Class<? extends Annotation> value(); }");
			register("java.util.function.Function",
					"package java.util.function; public interface Function<T,R> { R apply(T value); static <T> Function<T,T> identity() { return null; } }");
			register("java.util.function.Predicate",
					"package java.util.function; public interface Predicate<T> { boolean test(T value); default Predicate<T> and(Predicate<? super T> other) { return null; } default Predicate<T> or(Predicate<? super T> other) { return null; } default Predicate<T> negate() { return null; } }");
			register("java.util.function.Consumer",
					"package java.util.function; public interface Consumer<T> { void accept(T value); }");
			register("java.util.function.Supplier",
					"package java.util.function; public interface Supplier<T> { T get(); }");
			register("java.util.function.BiFunction",
					"package java.util.function; public interface BiFunction<T,U,R> { R apply(T first, U second); }");
			register("java.util.function.UnaryOperator",
					"package java.util.function; public interface UnaryOperator<T> extends Function<T,T> {}");
			register("java.util.stream.Stream",
					"package java.util.stream; public interface Stream<T> extends java.lang.AutoCloseable { Stream<T> filter(java.util.function.Predicate<? super T> predicate); <R> Stream<R> map(java.util.function.Function<? super T, ? extends R> mapper); Stream<T> distinct(); Stream<T> sorted(); java.util.Optional<T> findFirst(); java.util.List<T> toList(); long count(); boolean anyMatch(java.util.function.Predicate<? super T> predicate); boolean allMatch(java.util.function.Predicate<? super T> predicate); <R,A> R collect(Collector<? super T,A,R> collector); void forEach(java.util.function.Consumer<? super T> action); void close(); static <T> Stream<T> of(T... values) { return null; } }");
			register("java.util.stream.Collector",
					"package java.util.stream; public interface Collector<T,A,R> {}");
			register("java.util.stream.Collectors",
					"package java.util.stream; public final class Collectors { public static <T> Collector<T,?,java.util.List<T>> toList() { return null; } public static <T> Collector<T,?,java.util.Set<T>> toSet() { return null; } public static Collector<java.lang.CharSequence,?,String> joining(CharSequence delimiter) { return null; } public static <T,K,U> Collector<T,?,java.util.Map<K,U>> toMap(java.util.function.Function<? super T, ? extends K> keyMapper, java.util.function.Function<? super T, ? extends U> valueMapper) { return null; } }");
			register("java.util.concurrent.Callable",
					"package java.util.concurrent; public interface Callable<V> { V call() throws java.lang.Exception; }");
			register("java.util.concurrent.Future",
					"package java.util.concurrent; public interface Future<V> { boolean cancel(boolean mayInterruptIfRunning); boolean isCancelled(); boolean isDone(); V get() throws java.lang.Exception; }");
			register("java.util.concurrent.Executor",
					"package java.util.concurrent; public interface Executor { void execute(java.lang.Runnable command); }");
			register("java.util.concurrent.ExecutorService",
					"package java.util.concurrent; public interface ExecutorService extends Executor { <T> Future<T> submit(Callable<T> task); Future<?> submit(java.lang.Runnable task); void shutdown(); }");
			register("java.util.concurrent.Executors",
					"package java.util.concurrent; public final class Executors { public static ExecutorService newSingleThreadExecutor() { return null; } public static ExecutorService newFixedThreadPool(int nThreads) { return null; } }");
			register("java.util.concurrent.TimeUnit",
					"package java.util.concurrent; public enum TimeUnit { NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS; public long toMillis(long duration) { return 0; } }");
			register("java.util.concurrent.CompletableFuture",
					"package java.util.concurrent; public class CompletableFuture<T> implements Future<T> { public static <U> CompletableFuture<U> completedFuture(U value) { return null; } public static <U> CompletableFuture<U> supplyAsync(java.util.function.Supplier<U> supplier) { return null; } public static <U> CompletableFuture<U> supplyAsync(java.util.function.Supplier<U> supplier, Executor executor) { return null; } public <U> CompletableFuture<U> thenApply(java.util.function.Function<? super T, ? extends U> fn) { return null; } public CompletableFuture<Void> thenAccept(java.util.function.Consumer<? super T> action) { return null; } public T join() { return null; } public T get() throws java.lang.Exception { return null; } public boolean cancel(boolean mayInterruptIfRunning) { return false; } public boolean isCancelled() { return false; } public boolean isDone() { return false; } }");
			register("java.nio.file.Path",
					"package java.nio.file; public interface Path extends java.lang.Comparable<Path> { Path getFileName(); Path resolve(String other); Path resolve(Path other); Path normalize(); String toString(); }");
			register("java.nio.file.Paths",
					"package java.nio.file; public final class Paths { public static Path get(String first, String... more) { return null; } }");
			register("java.nio.file.Files",
					"package java.nio.file; public final class Files { public static boolean exists(Path path, LinkOption... options) { return false; } public static boolean isDirectory(Path path, LinkOption... options) { return false; } public static java.util.List<String> readAllLines(Path path) throws java.io.IOException { return null; } public static byte[] readAllBytes(Path path) throws java.io.IOException { return null; } public static Path write(Path path, byte[] bytes, OpenOption... options) throws java.io.IOException { return null; } }");
			register("java.nio.file.OpenOption", "package java.nio.file; public interface OpenOption {}");
			register("java.nio.file.LinkOption", "package java.nio.file; public enum LinkOption { NOFOLLOW_LINKS }");
			register("java.nio.file.StandardOpenOption",
					"package java.nio.file; public enum StandardOpenOption implements OpenOption { READ, WRITE, CREATE, APPEND, TRUNCATE_EXISTING }");
			register("java.time.temporal.TemporalAccessor", "package java.time.temporal; public interface TemporalAccessor {}");
			register("java.time.LocalDate",
					"package java.time; public final class LocalDate implements java.lang.Comparable<LocalDate>, java.time.temporal.TemporalAccessor { public static LocalDate now() { return null; } public static LocalDate parse(CharSequence text) { return null; } public LocalDate plusDays(long days) { return null; } public int compareTo(LocalDate other) { return 0; } public String toString() { return null; } }");
			register("java.time.LocalDateTime",
					"package java.time; public final class LocalDateTime implements java.lang.Comparable<LocalDateTime>, java.time.temporal.TemporalAccessor { public static LocalDateTime now() { return null; } public static LocalDateTime parse(CharSequence text) { return null; } public LocalDateTime plusDays(long days) { return null; } public int compareTo(LocalDateTime other) { return 0; } public String toString() { return null; } }");
			register("java.time.Instant",
					"package java.time; public final class Instant implements java.lang.Comparable<Instant>, java.time.temporal.TemporalAccessor { public static Instant now() { return null; } public static Instant parse(CharSequence text) { return null; } public Instant plusSeconds(long seconds) { return null; } public int compareTo(Instant other) { return 0; } public String toString() { return null; } }");
			register("java.time.Duration",
					"package java.time; public final class Duration implements java.lang.Comparable<Duration> { public static Duration ofSeconds(long seconds) { return null; } public static Duration between(Instant startInclusive, Instant endExclusive) { return null; } public long toSeconds() { return 0; } public int compareTo(Duration other) { return 0; } }");
			register("java.time.format.DateTimeFormatter",
					"package java.time.format; public final class DateTimeFormatter { public static final DateTimeFormatter ISO_LOCAL_DATE = null; public static DateTimeFormatter ofPattern(String pattern) { return null; } public String format(java.time.temporal.TemporalAccessor temporal) { return null; } }");
		}

		@Override
		public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
			return find(qualifiedName(compoundTypeName));
		}

		@Override
		public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
			String pkg = qualifiedName(packageName);
			return find(pkg.isEmpty() ? new String(typeName) : pkg + "." + new String(typeName));
		}

		@Override
		public boolean isPackage(char[][] parentPackageName, char[] packageName) {
			String parent = qualifiedName(parentPackageName);
			String qualified = parent.isEmpty() ? new String(packageName) : parent + "." + new String(packageName);
			return packages.containsKey(qualified);
		}

		@Override
		public void cleanup() {
		}

		private NameEnvironmentAnswer find(String qualifiedName) {
			if (isJdkApi(qualifiedName)) {
				NameEnvironmentAnswer binaryType = binaryAnswer(qualifiedName);
				if (binaryType != null) {
					return binaryType;
				}
			}
			MemoryCompilationUnit sourceType = sourceTypes.get(qualifiedName);
			if (sourceType != null) {
				return new NameEnvironmentAnswer(sourceType, null);
			}
			return binaryAnswer(qualifiedName);
		}

		private static boolean isJdkApi(String qualifiedName) {
			return qualifiedName.startsWith("java.")
					|| qualifiedName.startsWith("javax.")
					|| qualifiedName.startsWith("jdk.")
					|| qualifiedName.startsWith("com.sun.")
					|| qualifiedName.startsWith("sun.")
					|| qualifiedName.startsWith("org.w3c.")
					|| qualifiedName.startsWith("org.xml.");
		}

		private NameEnvironmentAnswer binaryAnswer(String qualifiedName) {
			String resourceName = qualifiedName.replace('.', '/') + ".class";
			InputStream input = EcjCompilerDiagnostics.class.getClassLoader().getResourceAsStream(resourceName);
			if (input == null) {
				return null;
			}
			try {
				byte[] bytes = readAll(input);
				return new NameEnvironmentAnswer(ClassFileReader.read(bytes, resourceName, true), null);
			} catch (ClassFormatException | IOException ex) {
				return null;
			}
		}

		private static byte[] readAll(InputStream input) throws IOException {
			try {
				ByteArrayOutputStream output = new ByteArrayOutputStream();
				byte[] buffer = new byte[4096];
				while (true) {
					int read = input.read(buffer);
					if (read < 0) {
						return output.toByteArray();
					}
					output.write(buffer, 0, read);
				}
			} finally {
				input.close();
			}
		}

		private void registerJavaLang(String simpleName, String source) {
			register("java.lang." + simpleName, source);
		}

		private void registerJdkSignaturePackages() {
			InputStream input = EcjCompilerDiagnostics.class.getClassLoader()
					.getResourceAsStream("org/eclipse/jdt/ls/web/internal/resources/jdk-signature.resources");
			if (input == null) {
				return;
			}
			try {
				String content = new String(readAll(input), "UTF-8");
				int start = 0;
				while (start < content.length()) {
					int end = content.indexOf('\n', start);
					if (end < 0) {
						end = content.length();
					}
					String resource = content.substring(start, end).trim();
					int slash = resource.lastIndexOf('/');
					if (slash > 0 && resource.endsWith(".class")) {
						registerPackage(resource.substring(0, slash).replace('/', '.'));
					}
					start = end + 1;
				}
			} catch (IOException ignored) {
			}
		}

		private void register(String qualifiedName, String source) {
			int dot = qualifiedName.lastIndexOf('.');
			String packageName = dot >= 0 ? qualifiedName.substring(0, dot) : "";
			String simpleName = dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
			registerPackage(packageName);
			sourceTypes.put(qualifiedName, new MemoryCompilationUnit(source, simpleName + ".java", packageChars(packageName)));
		}

		private void register(MemoryCompilationUnit unit) {
			String packageName = qualifiedName(unit.getPackageName());
			registerPackage(packageName);
			String[] topLevelTypeNames = unit.topLevelTypeNames();
			if (topLevelTypeNames.length == 0) {
				registerSourceType(packageName, new String(unit.getMainTypeName()), unit);
				return;
			}
			for (String typeName : topLevelTypeNames) {
				registerSourceType(packageName, typeName, unit.alias(typeName));
			}
		}

		private void registerSourceType(String packageName, String typeName, MemoryCompilationUnit unit) {
			sourceTypes.put(packageName.isEmpty() ? typeName : packageName + "." + typeName, unit);
		}

		private void registerPackage(String packageName) {
			packages.put(packageName, Boolean.TRUE);
			int dot = packageName.lastIndexOf('.');
			while (dot > 0) {
				packageName = packageName.substring(0, dot);
				packages.put(packageName, Boolean.TRUE);
				dot = packageName.lastIndexOf('.');
			}
		}

		private static char[][] packageChars(String packageName) {
			if (packageName == null || packageName.isEmpty()) {
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

		private static String qualifiedName(char[][] parts) {
			if (parts == null || parts.length == 0) {
				return "";
			}
			StringBuilder result = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					result.append('.');
				}
				result.append(parts[i]);
			}
			return result.toString();
		}
	}
}
