package net.pnyxter.actor.dispatcher;

// TODO: Non-actor threads?
// TODO: Follow supervisors between threads?
public class ActorThreads {

	private static final ThreadLocal<ActorRef> threadActor = new ThreadLocal<>();

	public static ActorRef getCurrentActor() {
		return threadActor.get();
	}

}
