package net.pnyxter.actor.system;

import net.pnyxter.actor.dispatcher.ActorRef;

public class Announcement {
	private final ActorRef actor;
	private final Thread thread;

	public Announcement(ActorRef actor, Thread thread) {
		this.actor = actor;
		this.thread = thread;
	}

	public ActorRef getActor() {
		return actor;
	}

	public Thread getThread() {
		return thread;
	}

	@Override
	public String toString() {
		return "(A#" + actor.hashCode() + "->" + thread.getName() + ")";
	}
}