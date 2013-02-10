package net.pnyxter.actor;

import java.util.Collection;
import java.util.concurrent.Future;

import net.pnyxter.actor.dispatcher.ActorQueue;
import net.pnyxter.actor.dispatcher.ActorRef;
import net.pnyxter.actor.dispatcher.ActorThreads;
import net.pnyxter.actor.system.ActorSystem;
import net.pnyxter.immutalizer.Immutalizer;

@Actor
public class Logger {

	private final long __in__actor__actorId = ActorSystem.nextActorId();
	private transient final ActorQueue __in__actor__queue;
	private transient final ActorRef __in__actor__spawnwer;

	private final class Caller_logString extends ActorQueue.Action {

		private final String message;
		private final long values;

		Caller_logString(String message, long values) {
			this.message = Immutalizer.ensureImmutable(String.class, message);
			this.values = values;
		}

		@Override
		public void execute() {
			__internal__log(message, values);
		}
	}

	public Logger() {
		__in__actor__queue = new ActorQueue();
		__in__actor__spawnwer = ActorThreads.getCurrentActor();
	}

	@Inbox
	public Future<Integer> getSize() {
		return ActorUtil.returnFuture(7);
	}

	@Inbox
	public void log(String message, long values) {
		__in__actor__queue.add(new Caller_logString(message, values));
	}

	void __internal__log(String message, long values) {
		System.out.println(message);
	}
}
