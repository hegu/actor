package net.pnyxter.actor.dispatcher;

public interface ActorRef {
	Thread getAssignedThread();

	void setAssignedThread(Thread thread);
}
