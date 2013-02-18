package net.pnyxter.actor.system;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import net.pnyxter.actor.dispatcher.ActorQueue.Action;
import net.pnyxter.actor.dispatcher.ActorRef;
import net.pnyxter.multicore.BroadcastQueue;
import net.pnyxter.multicore.UnsafeBroadcastQueue;

public class ActorSystem implements AutoCloseable {

	public enum ProcessType {
		TRY_SINGLE, WAIT_SINGLE, UNTIL_NO_WORK, UNTIL_SHUTDOWN
	};

	public enum ProcessStatus {
		EMPTY, PROCESSED, CLOSED
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
		BlockingQueue<Action> actions = new LinkedBlockingQueue<>();
		BroadcastQueue.Follower<Announcement> assignmentAnnouncementsFollower = assignmentAnnouncements.follower();

		@Override
		public String toString() {
			StringBuilder buffer = new StringBuilder();

			buffer.append("ThQ: \n");
			for (Action a : actions) {
				buffer.append("\t#").append(a.hashCode()).append("\n");
			}
			buffer.append("AgQ: \n");
			for (Map.Entry<ActorRef, Collection<Action>> e : actorQueuedActions.entrySet()) {
				buffer.append("\tA#").append(e.getKey().hashCode()).append("\n");
				for (Action a : e.getValue()) {
					buffer.append("\t\t#").append(a.hashCode()).append("\n");
				}
			}
			buffer.append("Ann: \n\t").append(assignmentAnnouncementsFollower);

			return buffer.toString();
		}
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
		ActorThreadContext context = getThreadContext();

		System.out.println("ADD #" + a.hashCode());

		Thread destinationThread = a.getActorRef().getAssignedThread();

		if (destinationThread == null) {
			destinationThread = processAnnouncement(context, a.getActorRef());
		}

		if (destinationThread == null) {
			Map<ActorRef, Collection<Action>> actorQueues = context.actorQueuedActions;

			Collection<Action> actorQueue = actorQueues.get(a.getActorRef());
			if (actorQueue == null) {
				actorQueues.put(a.getActorRef(), actorQueue = new LinkedList<Action>());
			}
			actorQueue.add(a);
		} else if (destinationThread == Thread.currentThread()) {
			context.actions.add(a);
		} else {
			// XXX: Accessing other thread context. Looks odd even this is
			// according to design and perfectly thread safe operation.
			BlockingQueue<Action> remoteActions = threadContexts.get(destinationThread).actions;
			Collection<Action> queuedActions = context.actorQueuedActions.remove(a.getActorRef());
			if (queuedActions != null) {
				for (Action queued : queuedActions) {
					remoteActions.add(queued);
				}
			}
			remoteActions.add(a);
		}
	}

	private static Thread processAnnouncement(ActorThreadContext context, ActorRef actor) {
		for (;;) {
			Announcement announcement = context.assignmentAnnouncementsFollower.poll();
			if (announcement != null) {
				ActorRef announcedActor = announcement.getActor();
				Thread announcedThread = announcement.getThread();

				announcedActor.setAssignedThread(announcedThread);

				Collection<Action> queuedActions = context.actorQueuedActions.remove(announcedActor);
				if (queuedActions != null) {
					BlockingQueue<Action> remoteActions = threadContexts.get(announcedThread).actions;
					for (Action queued : queuedActions) {
						remoteActions.add(queued);
					}
				}

				if (announcedActor == actor) {
					return announcedThread;
				}
			} else {
				return null;
			}
		}
	}

	/**
	 * @param type
	 * @return {@code true} if more work is to be processed
	 * @throws InterruptedException
	 */
	public static boolean process(ProcessType type) throws InterruptedException {
		ActorThreadContext context = getThreadContext();

		switch (type) {
		case TRY_SINGLE:
			return process(context, false) == ProcessStatus.PROCESSED;

		case WAIT_SINGLE:
			return process(context, true) == ProcessStatus.PROCESSED;

		case UNTIL_NO_WORK:
			while (process(context, false) == ProcessStatus.PROCESSED) {
				// Empty
			}
			return false;

		case UNTIL_SHUTDOWN:
			while (process(context, true) != ProcessStatus.CLOSED) {
				// Empty
			}
			return false;
		default:
			throw new IllegalArgumentException("Unknown type: " + type);
		}

	}

	private static ProcessStatus process(ActorThreadContext context, boolean block) throws InterruptedException {
		processAnnouncement(context, null);

		Action a;
		if (block) {
			a = context.actions.take();
		} else {
			a = context.actions.poll();
		}

		if (a == null) {
			return ProcessStatus.EMPTY;
		}

		System.out.println("RUN #" + a.hashCode());
		a.execute();

		return ProcessStatus.PROCESSED;
	}

	@Override
	public void close() {
		shutdown();
	}

	public void shutdown() {
		throw new UnsupportedOperationException("Closed not yet implemented");
	}

	private static class ActorThread extends Thread {
		public ActorThread(String name) {
			super(name);
		}

		@Override
		public void run() {
			try {
				process(ProcessType.UNTIL_SHUTDOWN);
			} catch (InterruptedException e) {
				// Just complete thread after clearing interrupted flag
				Thread.interrupted();
			}
		}
	}
}
