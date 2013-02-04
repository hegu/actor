package net.pnyxter.actor;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import net.pnyxter.actor.dispatcher.ActorQueue;
import net.pnyxter.immutalizer.Immutalizer;

@Actor
public class Logger {

	private final ActorQueue queue = new ActorQueue();

	private final class Caller_logString extends ActorQueue.Action {

		private final String message;
		private final Collection<String> values;

		Caller_logString(String message, Collection<String> values) {
			this.message = Immutalizer.ensureImmutable(String.class, message);
			this.values = Immutalizer.ensureImmutable(Collection.class, values);
		}

		@Override
		public void execute() {
			__internal__log(message, values);
		}
	}

	@Inbox
	public <T extends String, Y extends Set<Integer>> Map<Integer, T> getSize() {
		return null;
		// return ActorUtil.returnFuture(7);
	}

	@Inbox
	public void log(String message, Collection<String> values) {
		queue.add(new Caller_logString(message, values));
		System.out.println(message);
	}

	void __internal__log(String message, Collection<String> values) {
		System.out.println(message);
	}
}
