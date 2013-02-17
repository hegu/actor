package net.pnyxter.actor.system;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import net.pnyxter.actor.dispatcher.ActorQueue.Action;
import net.pnyxter.actor.dispatcher.ActorRef;
import net.pnyxter.multicore.BroadcastQueue;
import net.pnyxter.multicore.UnsafeBroadcastQueue;

public class ActorSystem {

	public enum ProcessType {
		TRY_SINGLE, WAIT_SINGLE, UNTIL_NO_WORK, UNTIL_SHUTDOWN
	};

	private static final long STEP = 0x100;

	private static final AtomicLong nextChunkId = new AtomicLong(0);

	private static final ThreadLocal<Long> currentLocalChunk = new ThreadLocal<Long>() {
		@Override
		protected Long initialValue() {
			return nextChunk();
		}
	};

	private static final ThreadLocal<Long> localDiff = new ThreadLocal<Long>() {
		@Override
		protected Long initialValue() {
			return 0L;
		}
	};

	private static final Set<Class<?>> actorClasses = new CopyOnWriteArraySet<>();

	public static boolean isActor(Class<?> actorClass) {
		return actorClasses.contains(actorClass);
	}

	// TODO: New actor classes will not be anounced to all threads
	public static void registerActorClass(Class<?> actorClass) {
		actorClasses.add(actorClass);
	}

	private static long nextChunk() {
		for (;;) {
			long r = nextChunkId.get();
			long n = r + STEP;

			if (nextChunkId.compareAndSet(r, n)) {
				return r;
			}
		}
	}

	public static long nextActorId() {
		long v = localDiff.get();

		long n = v + 1;

		long c;

		if (n == STEP) {
			currentLocalChunk.set(c = nextChunk());
			n = 0;
		} else {
			c = currentLocalChunk.get();
		}

		return c + n;
	}

	private static class ActorThreadContext {
		Map<ActorRef, Collection<Action>> actorQueuedActions = new HashMap<>();
		ConcurrentLinkedQueue<Action> actions = new ConcurrentLinkedQueue<>();
		BroadcastQueue.Follower<Announcement> assignmentAnnouncementsFollower = assignmentAnnouncements.follower();
	}

	private static final ConcurrentHashMap<Thread, ActorThreadContext> threadContexts = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Thread> idleQueue = new ConcurrentLinkedQueue<>();

	private static final BroadcastQueue<Announcement> assignmentAnnouncements = new UnsafeBroadcastQueue<>();

	public static void start(int threads) {
		for (int i = 0; 0 < threads; i++) {
			new ActorThread("ActorThread-" + (1 + i)).start();
		}
	}

	private static ActorThreadContext getThreadContext() {
		Thread thread = Thread.currentThread();
		ActorThreadContext context = threadContexts.get(thread);
		if (context == null) {
			threadContexts.put(thread, context = new ActorThreadContext());
		}
		return context;
	}

	/**
	 * How is the messages sent to an actor processed in expected order.
	 * <ul>
	 * <li>If destination actor is assigned to current thread<br>
	 * => Post job on thread queue</li>
	 * <li>If destination actor is unassigned<br>
	 * <ul>
	 * <li>If destination actor is assigned to current thread<br>
	 * => Post job on thread queue</li>
	 * <li>If destination actor is assigned to current thread<br>
	 * => Post job on thread queue</li>
	 * </ul>
	 * </li>
	 * <li>If destination actor is assigned to another thread<br>
	 * => Send locally queued messages on the destination actor followed by this
	 * message</li>
	 * <li>When receiving a assignment announcement<br>
	 * => Send locally queued messages on the assigned actor
	 * <li>Possible optimization<br>
	 * => When you are sure an actor has not been shared with other thread local
	 * assignment without announcement is safe</li>
	 * 
	 * </ul>
	 * 
	 * @param a
	 */
	public static void add(Action a) {
		System.out.println("ADD #" + a.hashCode());

		Thread currentThread = Thread.currentThread();
		Thread destinationThread = a.getActorRef().getAssignedThread();

		if (destinationThread == null) {
			announcement_loop: for (;;) {
				Announcement announcement = getThreadContext().assignmentAnnouncementsFollower.poll();
				if (announcement != null) {
					announcement.getActor().setAssignedThread(announcement.getThread());
					if (announcement.getActor() == a.getActorRef()) {
						destinationThread = announcement.getThread();
						break announcement_loop;
					}
				} else {
					break announcement_loop;
				}
			}
		}

		if (destinationThread == null) {
			Map<ActorRef, Collection<Action>> actorQueues = getThreadContext().actorQueuedActions;

			Collection<Action> actorQueue = actorQueues.get(a.getActorRef());
			if (actorQueue == null) {
				actorQueues.put(a.getActorRef(), actorQueue = new LinkedList<Action>());
			}
			actorQueue.add(a);
		} else if (destinationThread == currentThread) {
			getThreadContext().actions.add(a);
		} else {
			ConcurrentLinkedQueue<Action> remoteActions = threadContexts.get(destinationThread).actions;
			Collection<Action> queuedActions = getThreadContext().actorQueuedActions.remove(a.getActorRef());
			if (queuedActions != null) {
				for (Action queued : queuedActions) {
					remoteActions.add(queued);
				}
			}
			remoteActions.add(a);
		}
	}

	/**
	 * @param type
	 * @return {@code true} if more work is to be processed
	 */
	public static boolean process(ProcessType type) {
		ActorThreadContext context = getThreadContext();

		switch (type) {
		case TRY_SINGLE:
		case WAIT_SINGLE: {
			Action a = null; // queue.pollFirst();
			if (a != null) {
				System.out.println("RUN #" + a.hashCode());
				a.execute();
			} else {
				return false;
			}
			return true; // !queue.isEmpty();
		}
		case UNTIL_NO_WORK:
		case UNTIL_SHUTDOWN:
			for (;;) {
				Action a = null; // queue.pollFirst();
				if (a != null) {
					System.out.println("RUN #" + a.hashCode());
					a.execute();
				} else {
					return false;
				}
			}
		}

		return true; // !queue.isEmpty();
	}

	private static class ActorThread extends Thread {
		public ActorThread(String name) {
			super(name);
		}

		@Override
		public void run() {
			process(ProcessType.UNTIL_SHUTDOWN);
		}
	}
}
