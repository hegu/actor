package net.pnyxter.actor.dispatcher;

public interface ActorRef {
	Thread getAssignedThread();

	boolean setAssignedThread(Thread thread);

	void updateAssignedThread(Thread thread);
}
