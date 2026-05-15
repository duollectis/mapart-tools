package org.duollectis.mapart.tools.nativee;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * A utility class to bridge Java and Native (C++) code using Project Panama (FFM API).
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

	public <T> T create(Class<T> iface) {
		if (!methods.isEmpty()) {
			throw new RuntimeException("Wrapper created already!");
		}

		for (Method m : iface.getDeclaredMethods()) {

			if (!m.isAnnotationPresent(NativeMethod.class)) {
				continue;
			}

			String name = m.getAnnotation(NativeMethod.class).value();

			if (name.isBlank()) {
				name = m.getName();
			}

			// Определяем возвращаемый тип
			Class<?> returnType = m.getReturnType();
			MemoryLayout res = map(returnType);

			// Определяем аргументы
			MemoryLayout[] args = new MemoryLayout[m.getParameterCount()];

			for (int i = 0; i < m.getParameterCount(); i++) {
				args[i] = map(m.getParameterTypes()[i]);
			}

			FunctionDescriptor desc = (res == null) ? FunctionDescriptor.ofVoid(args)
			                                        : FunctionDescriptor.of(res, args);

			// Ищем функцию в C++
			MethodHandle handle = lib
					.find(name)
					.map(addr -> linker.downcallHandle(addr, desc, Linker.Option.isTrivial()))
					.orElseThrow(() -> new RuntimeException("C++ function not found: " + m.getName()));

			methods.put(m.getName(), handle);
		}

		Object proxy = Proxy.newProxyInstance(
				iface.getClassLoader(),
				new Class[]{iface},
				(_, method, parameters) -> handle(method, parameters)
		);

		return (T) proxy;
	}

	private Object handle(Method method, Object[] parameters) throws Throwable {
		if (closed) {
			throw new RuntimeException("Native is closed!");
		}

		if (!method.isAnnotationPresent(NativeMethod.class) && method.getName().equals("close")) {
			arena.close();
			closed = true;
			return null;
		}

		MethodHandle handle = methods.get(method.getName());

		if (handle == null) {
			throw new RuntimeException("Method not found");
		}

		// Нам нужно знать типы параметров метода, чтобы правильно их трансформировать
		Object[] transformedArgs = new Object[parameters.length];

		for (int i = 0; i < parameters.length; i++) {
			Object parameter = parameters[i];

			if (parameter == null) {
				continue;
			}

			if (parameter.getClass().isArray()) {
				// 1. Считаем общее количество элементов
				long totalElements = getTotalElements(parameter);

				// 2. Определяем базовый тип (Layout)
				ValueLayout layout = getBaseLayout(parameter);

				if (layout == null) {
					transformedArgs[i] = parameter; // Если тип не поддерживается
				}
				else {
					// 3. Выделяем память
					MemorySegment seg = arena.allocate(totalElements * layout.byteSize());

					// 4. Пакуем рекурсивно
					packRecursive(parameter, seg, 0, layout);

					transformedArgs[i] = seg;
				}
			}
			else {
				transformedArgs[i] = parameter;
			}
		}

		// 2. Сам вызов нативного метода
		Object result = handle.invokeWithArguments(transformedArgs);

		// 3. Копирование данных ИЗ нативной памяти обратно в Java-массивы (УНИВЕРСАЛЬНО)
		for (int i = 0; i < parameters.length; i++) {
			Object originalArg = parameters[i];
			Object transformed = transformedArgs[i];

			// Если это был массив и мы его превращали в MemorySegment
			if (originalArg != null
					&& originalArg.getClass().isArray()
					&& transformed instanceof MemorySegment seg
			) {
				// Используем рекурсивную утилиту для распаковки!
				unpackRecursive(seg, originalArg, 0);
			}
		}

		return result;
	}

	private static MemoryLayout map(Class<?> c) {
		if (c == void.class)
			return null;
		if (c == int.class)
			return ValueLayout.JAVA_INT;
		if (c == long.class)
			return ValueLayout.JAVA_LONG;
		if (c == double.class)
			return ValueLayout.JAVA_DOUBLE;
		if (c == float.class)
			return ValueLayout.JAVA_FLOAT;
		if (c == byte.class)
			return ValueLayout.JAVA_BYTE;
		if (c == short.class)
			return ValueLayout.JAVA_SHORT;
		if (c == boolean.class)
			return ValueLayout.JAVA_BOOLEAN;

		// Все, что является указателем (массивы, MemorySegment)
		if (c == MemorySegment.class || c.isArray()) {
			return ValueLayout.ADDRESS;
		}

		throw new RuntimeException("Unsupported type: " + c.getName());
	}

	public static <T extends NativeWrapper> T create(Class<T> wrapper, File lib) {
		return new NativeBridge(lib).create(wrapper);
	}

	private static long getTotalElements(Object array) {
		if (array == null) {
			return 0;
		}

		if (!array.getClass().isArray()) {
			return 1;
		}

		long count = 0;
		int length = Array.getLength(array);

		for (int i = 0; i < length; i++) {
			Object element = Array.get(array, i);

			if (element != null && element.getClass().isArray()) {
				count += getTotalElements(element);

			}
			else {
				count++;
			}
		}

		return count;
	}

	// Вспомогательный метод для определения типа данных
	private static ValueLayout getBaseLayout(Object array) {
		Class<?> clazz = array.getClass();
		while (clazz.isArray()) {
			clazz = clazz.getComponentType();
		}
		if (clazz == double.class)
			return ValueLayout.JAVA_DOUBLE;
		if (clazz == float.class)
			return ValueLayout.JAVA_FLOAT;
		if (clazz == int.class)
			return ValueLayout.JAVA_INT;
		if (clazz == long.class)
			return ValueLayout.JAVA_LONG;
		if (clazz == byte.class)
			return ValueLayout.JAVA_BYTE;
		return null;
	}

	private static long packRecursive(Object array, MemorySegment segment, long offset, ValueLayout layout) {
		if (array == null)
			return offset;

		// Если это "дно" — массив примитивов
		if (isPrimitiveArray(array)) {
			MemorySegment source = getSegmentFromArray(array);
			long bytes = source.byteSize();

			segment.asSlice(offset, bytes).copyFrom(source);
			return offset + bytes;
		}
		else {
			// Если это массив массивов
			int length = Array.getLength(array);
			for (int i = 0; i < length; i++) {
				offset = packRecursive(Array.get(array, i), segment, offset, layout);
			}
		}
		return offset;
	}

	// Универсальное получение сегмента из массива любого типа
	private static MemorySegment getSegmentFromArray(Object array) {
		return switch (array) {
			case double[] a -> MemorySegment.ofArray(a);
			case int[] a -> MemorySegment.ofArray(a);
			case byte[] a -> MemorySegment.ofArray(a);
			case float[] a -> MemorySegment.ofArray(a);
			case long[] a -> MemorySegment.ofArray(a);
			default -> throw new IllegalArgumentException("Unsupported array type");
		};
	}

	private static boolean isPrimitiveArray(Object array) {
		Class<?> c = array.getClass();
		return c.isArray() && c.getComponentType().isPrimitive();
	}

	private static long unpackRecursive(MemorySegment segment, Object array, long offset) {
		if (isPrimitiveArray(array)) {
			MemorySegment dest = getSegmentFromArray(array);
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
}
