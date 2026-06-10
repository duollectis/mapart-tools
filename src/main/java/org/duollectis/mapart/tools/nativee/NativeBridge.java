package org.duollectis.mapart.tools.nativee;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * Мост между Java и нативным C++ кодом через Project Panama (FFM API).
 * Создаёт динамический прокси для интерфейса, аннотированного {@link NativeMethod},
 * автоматически маршалируя Java-массивы в нативную память и обратно.
 */
@SuppressWarnings({"preview", "Since15"})
public class NativeBridge {

	private final Arena arena = Arena.ofShared();
	private final SymbolLookup lib;
	private final Linker linker = Linker.nativeLinker();
	private final Map<String, MethodHandle> methods = new HashMap<>();

	private boolean closed;

	private NativeBridge(File lib) {
		this.lib = SymbolLookup.libraryLookup(lib.getPath(), arena);
	}

	/**
	 * Создаёт прокси-обёртку для нативного интерфейса.
	 * Каждый метод, помеченный {@link NativeMethod}, связывается с соответствующей C++ функцией.
	 *
	 * @param iface интерфейс-контракт нативных методов
	 * @param <T>   тип интерфейса
	 * @return прокси-объект, делегирующий вызовы в нативную библиотеку
	 */
	public <T> T create(Class<T> iface) {
		if (!methods.isEmpty()) {
			throw new RuntimeException("Обёртка уже создана!");
		}

		for (Method method : iface.getDeclaredMethods()) {
			if (method.isAnnotationPresent(NativeMethod.class)) {
				registerNativeMethod(method);
			}
		}

		Object proxy = Proxy.newProxyInstance(
			iface.getClassLoader(),
			new Class[]{iface},
			(_, method, parameters) -> handle(method, parameters)
		);

		return (T) proxy;
	}

	/**
	 * Создаёт {@link NativeBridge} и сразу возвращает прокси для указанного интерфейса.
	 *
	 * @param wrapper класс интерфейса-обёртки
	 * @param lib     файл нативной библиотеки
	 * @param <T>     тип обёртки
	 * @return готовый прокси-объект
	 */
	public static <T extends NativeWrapper> T create(Class<T> wrapper, File lib) {
		return new NativeBridge(lib).create(wrapper);
	}

	private void registerNativeMethod(Method method) {
		String nativeName = resolveNativeName(method);
		MemoryLayout returnLayout = map(method.getReturnType());
		MemoryLayout[] argLayouts = buildArgLayouts(method);

		FunctionDescriptor descriptor = returnLayout == null
			? FunctionDescriptor.ofVoid(argLayouts)
			: FunctionDescriptor.of(returnLayout, argLayouts);

		MethodHandle handle = lib
			.find(nativeName)
			.map(addr -> linker.downcallHandle(addr, descriptor))
			.orElseThrow(() -> new RuntimeException("C++ функция не найдена: " + method.getName()));

		methods.put(method.getName(), handle);
	}

	private String resolveNativeName(Method method) {
		String name = method.getAnnotation(NativeMethod.class).value();
		return name.isBlank() ? method.getName() : name;
	}

	private MemoryLayout[] buildArgLayouts(Method method) {
		MemoryLayout[] layouts = new MemoryLayout[method.getParameterCount()];

		for (int i = 0; i < method.getParameterCount(); i++) {
			layouts[i] = map(method.getParameterTypes()[i]);
		}

		return layouts;
	}

	private Object handle(Method method, Object[] parameters) throws Throwable {
		if (closed) {
			throw new RuntimeException("Нативный ресурс уже закрыт!");
		}

		if (isCloseMethod(method)) {
			arena.close();
			closed = true;
			return null;
		}

		MethodHandle handle = methods.get(method.getName());

		if (handle == null) {
			throw new RuntimeException("Метод не найден: " + method.getName());
		}

		Object[] transformedArgs = marshalArgs(parameters);
		Object result = handle.invokeWithArguments(transformedArgs);
		unmarshalArgs(parameters, transformedArgs);

		return result;
	}

	private boolean isCloseMethod(Method method) {
		return !method.isAnnotationPresent(NativeMethod.class) && method.getName().equals("close");
	}

	private Object[] marshalArgs(Object[] parameters) {
		Object[] transformed = new Object[parameters.length];

		for (int i = 0; i < parameters.length; i++) {
			Object param = parameters[i];

			if (param == null || !param.getClass().isArray()) {
				transformed[i] = param;
				continue;
			}

			ValueLayout layout = getBaseLayout(param);

			if (layout == null) {
				transformed[i] = param;
				continue;
			}

			// Выделяем память и упаковываем за один рекурсивный проход
			long byteSize = countBytes(param, layout);
			MemorySegment segment = arena.allocate(byteSize);
			packRecursive(param, segment, 0, layout);
			transformed[i] = segment;
		}

		return transformed;
	}

	private void unmarshalArgs(Object[] original, Object[] transformed) {
		for (int i = 0; i < original.length; i++) {
			Object originalArg = original[i];
			Object transformedArg = transformed[i];

			if (originalArg != null
				&& originalArg.getClass().isArray()
				&& transformedArg instanceof MemorySegment segment
			) {
				unpackRecursive(segment, originalArg, 0);
			}
		}
	}

	private static MemoryLayout map(Class<?> type) {
		if (type == void.class) return null;
		if (type == int.class) return ValueLayout.JAVA_INT;
		if (type == long.class) return ValueLayout.JAVA_LONG;
		if (type == double.class) return ValueLayout.JAVA_DOUBLE;
		if (type == float.class) return ValueLayout.JAVA_FLOAT;
		if (type == byte.class) return ValueLayout.JAVA_BYTE;
		if (type == short.class) return ValueLayout.JAVA_SHORT;
		if (type == boolean.class) return ValueLayout.JAVA_BOOLEAN;
		if (type == MemorySegment.class || type.isArray()) return ValueLayout.ADDRESS;

		throw new RuntimeException("Неподдерживаемый тип: " + type.getName());
	}

	/**
	 * Считает итоговый размер в байтах для упаковки многомерного массива в нативную память.
	 * Объединяет подсчёт элементов и вычисление размера в один проход вместо двух.
	 */
	private static long countBytes(Object array, ValueLayout layout) {
		if (array == null) {
			return 0;
		}

		if (isPrimitiveArray(array)) {
			return segmentOf(array).byteSize();
		}

		long total = 0;
		int length = Array.getLength(array);

		for (int i = 0; i < length; i++) {
			total += countBytes(Array.get(array, i), layout);
		}

		return total;
	}

	private static ValueLayout getBaseLayout(Object array) {
		Class<?> type = array.getClass();

		while (type.isArray()) {
			type = type.getComponentType();
		}

		if (type == double.class) return ValueLayout.JAVA_DOUBLE;
		if (type == float.class) return ValueLayout.JAVA_FLOAT;
		if (type == int.class) return ValueLayout.JAVA_INT;
		if (type == long.class) return ValueLayout.JAVA_LONG;
		if (type == byte.class) return ValueLayout.JAVA_BYTE;

		return null;
	}

	private static long packRecursive(Object array, MemorySegment segment, long offset, ValueLayout layout) {
		if (array == null) {
			return offset;
		}

		if (isPrimitiveArray(array)) {
			MemorySegment source = segmentOf(array);
			long bytes = source.byteSize();
			segment.asSlice(offset, bytes).copyFrom(source);
			return offset + bytes;
		}

		int length = Array.getLength(array);

		for (int i = 0; i < length; i++) {
			offset = packRecursive(Array.get(array, i), segment, offset, layout);
		}

		return offset;
	}

	private static long unpackRecursive(MemorySegment segment, Object array, long offset) {
		if (isPrimitiveArray(array)) {
			MemorySegment dest = segmentOf(array);
			long bytes = dest.byteSize();
			dest.copyFrom(segment.asSlice(offset, bytes));
			return offset + bytes;
		}

		int length = Array.getLength(array);

		for (int i = 0; i < length; i++) {
			offset = unpackRecursive(segment, Array.get(array, i), offset);
		}

		return offset;
	}

	private static boolean isPrimitiveArray(Object array) {
		Class<?> type = array.getClass();
		return type.isArray() && type.getComponentType().isPrimitive();
	}

	private static MemorySegment segmentOf(Object array) {
		return switch (array) {
			case double[] a -> MemorySegment.ofArray(a);
			case int[] a -> MemorySegment.ofArray(a);
			case byte[] a -> MemorySegment.ofArray(a);
			case float[] a -> MemorySegment.ofArray(a);
			case long[] a -> MemorySegment.ofArray(a);
			default -> throw new IllegalArgumentException("Неподдерживаемый тип массива: " + array.getClass());
		};
	}
}
