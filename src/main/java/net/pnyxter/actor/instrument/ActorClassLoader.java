package net.pnyxter.actor.instrument;

class ActorClassLoader extends ClassLoader {

	public ActorClassLoader(ClassLoader parent) {
		super(parent);
	}

	public Class<?> defineActorHelperClass(String name, byte[] b) {
		return defineClass(name, b, 0, b.length);
	}
}