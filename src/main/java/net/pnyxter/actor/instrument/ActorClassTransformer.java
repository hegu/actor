package net.pnyxter.actor.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;

public class ActorClassTransformer implements ClassFileTransformer {

	private static class ReflectionClassDefiner implements ClassDefiner {

		private final ClassLoader loader;
		private final Method defineClassMethod;
		private final ProtectionDomain protectionDomain;

		public ReflectionClassDefiner(ClassLoader loader, Method defineClassMethod, ProtectionDomain protectionDomain) {
			this.loader = loader;
			this.defineClassMethod = defineClassMethod;
			this.protectionDomain = protectionDomain;
		}

		@Override
		public void defineClass(String className, byte[] byteCode) {
			System.out.println("Defining a new class: " + className);

			try {
				defineClassMethod.invoke(loader, className, byteCode, 0, byteCode.length, protectionDomain);
			} catch (ClassFormatError e) {
				throw e;
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();

				throw new Error("Could not invoke define class: " + className, e);
			}
		}
	}

	private final ConcurrentHashMap<ClassLoader, Method> defineClassMethods = new ConcurrentHashMap<>();

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (loader == null || className.startsWith("java/") || className.startsWith("javax/") || className.startsWith("sun/")) {
			return null;
		}

		Method defineClassMethod = defineClassMethods.get(loader);
		if (defineClassMethod == null) {
			try {
				defineClassMethod = loader.loadClass(ClassLoader.class.getName()).getDeclaredMethod("defineClass", new Class<?>[] { String.class, byte[].class, int.class, int.class, ProtectionDomain.class });
				defineClassMethod.setAccessible(true);
			} catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
				throw new Error("Actor weaving not supported.", e);
			}
			defineClassMethods.putIfAbsent(loader, defineClassMethod);
		}

		return ActorWeaver.weaveActor(className, classfileBuffer, new ReflectionClassDefiner(loader, defineClassMethod, protectionDomain));
	}
}
