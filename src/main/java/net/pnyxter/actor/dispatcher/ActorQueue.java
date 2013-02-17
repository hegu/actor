package net.pnyxter.actor.dispatcher;

import net.pnyxter.actor.system.ActorSystem;

public class ActorQueue {

	private Thread actorThread = null;

	public interface Action {
		ActorRef getActorRef();

		void execute();
	}

	public void add(Action a) {
		ActorSystem.add(a);
	}

}
