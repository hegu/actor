package net.pnyxter.actor.dispatcher;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.pnyxter.actor.system.ActorSystem;

//TODO: Actions on non-instanciated actor?
//TODO: Actions on actor in transfer?
public class ActorQueue {

	private final ThreadLocal<ActorThread> actorThreads = new ThreadLocal<ActorThread>() {
		@Override
		protected ActorThread initialValue() {
			return new ActorThread();
		}
	};

	public class ActorThread {

	}

	public static abstract class Action {
		Action next;

		public abstract void execute();
	}

	static class InstantiationAction extends Action {
		Instantiation instantiation;

		@Override
		public void execute() {
			// TODO Auto-generated method stub

		}
	}

	static class QueuedInstantiation {
		AtomicReference<QueuedInstantiation> prev = new AtomicReference<>();
		AtomicBoolean processed = new AtomicBoolean();

		Action actions;
	}

	public static class Instantiation {
		private final Constructor<?> constructor;
		private final Object[] immutableParameters;

		public Instantiation(Constructor<?> constructor, Object[] immutableParameters) {
			this.constructor = constructor;
			this.immutableParameters = immutableParameters;
		}

		public Constructor<?> getConstructor() {
			return constructor;
		}

		public Object[] getImmutableParameters() {
			return immutableParameters;
		}

	}

	public void add(Action a) {
		ActorSystem.add(a);
	}

}
