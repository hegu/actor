package net.pnyxter.actor.instrument;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.concurrent.ConcurrentHashMap;

public class ActorClassTransformer implements ClassFileTransformer {

	private final ConcurrentHashMap<ClassLoader, ActorClassLoader> actorClassLoaders = new ConcurrentHashMap<>();

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		ActorClassLoader actorLoader = actorClassLoaders.get(loader);
		if (actorLoader == null) {
			ActorClassLoader newLoader = new ActorClassLoader(loader);

			actorLoader = actorClassLoaders.putIfAbsent(loader, newLoader);

			if (actorLoader == null) {
				actorLoader = newLoader;
			}
		}

		return ActorWeaver.weaveActor(className, classfileBuffer, actorLoader);
	}

}
