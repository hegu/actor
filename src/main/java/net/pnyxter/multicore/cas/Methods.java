package net.pnyxter.multicore.cas;

import java.lang.reflect.Field;

public class Methods {

	private static final sun.misc.Unsafe UNSAFE;

	static {
		try {
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) field.get(null);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	public static long objectFieldOffset(Class<?> objectClass, String fieldName) {
		try {
			return UNSAFE.objectFieldOffset(objectClass.getDeclaredField(fieldName));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	public static boolean compareAndSwap(Object obj, long field_offset, Object previousValue, Object newValue) {
		return UNSAFE.compareAndSwapObject(obj, field_offset, previousValue, newValue);
	}
}
