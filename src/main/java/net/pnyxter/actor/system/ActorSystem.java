package net.pnyxter.actor.system;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

import net.pnyxter.actor.dispatcher.ActorQueue.Action;
import net.pnyxter.actor.dispatcher.ActorRef;
import net.pnyxter.multicore.BroadcastQueue;
import net.pnyxter.multicore.JdkSimpleQueue;
import net.pnyxter.multicore.SimpleQueue;
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
		final Thread thread;
		final ConcurrentLinkedDeque<ActorRef> instantiations = new ConcurrentLinkedDeque<>();
		final Map<ActorRef, Collection<Action>> actorQueuedActions = new HashMap<>();
		final BlockingQueue<Action> actions = new LinkedBlockingQueue<>();
		final BroadcastQueue.Follower<Announcement> assignmentAnnouncementsFollower = assignmentAnnouncements.follower();

		int instantiationsCount = 0;
		int assignmentCount = 0;
		int actionCount = 0;

		public ActorThreadContext(Thread thread) {
			this.thread = thread;
		}

		@Override
		public String toString() {
			StringBuilder buffer = new StringBuilder();

			buffer.append(thread.getName()).append(": ");
			buffer.append(" instantiations:").append(instantiationsCount);
			buffer.append(" assignments:").append(instantiationsCount);
			buffer.append(" actions:").append(instantiationsCount);
			buffer.append("\n");

			if (!actions.isEmpty()) {
				buffer.append("ThQ: \n");
				for (Action a : actions) {
					buffer.append("\t#").append(a.hashCode()).append("\n");
				}
			}
			if (!actorQueuedActions.isEmpty()) {
				buffer.append("AgQ: \n");
				for (Map.Entry<ActorRef, Collection<Action>> e : actorQueuedActions.entrySet()) {
					buffer.append("\tA#").append(e.getKey().hashCode()).append("\n");
					for (Action a : e.getValue()) {
						buffer.append("\t\t#").append(a.hashCode()).append("\n");
					}
				}
			}
			if (!instantiations.isEmpty()) {
				buffer.append("I: \n");
				for (ActorRef a : instantiations) {
					buffer.append("\tA#").append(a.hashCode()).append("\n");
				}
			}
			String eventQueue = assignmentAnnouncementsFollower.toString();
			if (!"[]".equals(eventQueue)) {
				buffer.append("Ann: \n\t").append(eventQueue).append("\n");
			}
			return buffer.toString();
		}
	}

	public static final ConcurrentHashMap<Thread, ActorThreadContext> threadContexts = new ConcurrentHashMap<Thread, ActorThreadContext>() {
		private static final long serialVersionUID = 1L;

		@Override
		public String toString() {
			StringBuilder buffer = new StringBuilder();

			for (Iterator<ActorThreadContext> i = values().iterator(); i.hasNext();) {
				buffer.append(i.next());
				if (i.hasNext()) {
					buffer.append('\n');
				}
			}

			return buffer.toString();
		}
	};
	private static final SimpleQueue<Thread> idleQueue = new JdkSimpleQueue<>();

	private static final BroadcastQueue<Announcement> assignmentAnnouncements = new UnsafeBroadcastQueue<>();

	public static void start(int threads) {
		for (int i = 0; i < threads; i++) {
			new ActorThread("ActorThread-" + (1 + i)).start();
		}
	}

	private static ActorThreadContext getThreadContext() {
		Thread thread = Thread.currentThread();
		ActorThreadContext context = threadContexts.get(thread);
		if (context == null) {
			threadContexts.put(thread, context = new ActorThreadContext(thread));
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

		// System.out.println("ADD #" + a.hashCode());

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

				// System.out.println("Received announcment[" +
				// context.thread.getName() + "] A#" + announcedActor.hashCode()
				// + " -> " + announcedThread.getName());

				if (context.thread == announcedThread) {
					context.assignmentCount++;
				}

				announcedActor.updateAssignedThread(announcedThread);

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

	private static void assignLocalActor(ActorThreadContext context) {
		for (;;) {
			ActorRef actor = context.instantiations.pollFirst();
			if (actor == null) {
				return;
			}
			// XXX: Current design is only assigning from local thread
			if (actor.setAssignedThread(Thread.currentThread())) {
				// Local assignment should not trigger announcement

				context.assignmentCount++;

				Collection<Action> queuedActions = context.actorQueuedActions.remove(actor);
				if (queuedActions != null) {
					for (Action queued : queuedActions) {
						context.actions.add(queued);
					}
				}

				return;
			}
		}
	}

	private static boolean assignActorsToIdleThreads(ActorThreadContext context) {
		while (!context.instantiations.isEmpty()) {
			Thread idleThread = idleQueue.follower().poll();
			if (idleThread == null) {
				return true;
			}
			ActorRef a = context.instantiations.pollLast();
			if (a == null) {
				return false;
			}
			if (!a.setAssignedThread(idleThread)) {
				// No assignment happened - return thread to idle pool
				idleQueue.add(idleThread);
			} else {
				assignmentAnnouncements.add(new Announcement(a, idleThread));
				// System.out.println("Assigned to idle: A#" + a.hashCode() +
				// " -> " + idleThread.getName());
			}
		}
		return false;
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
		boolean announcedAsIdle = false;
		wait_loop: for (;;) {
			processAnnouncement(context, null);

			if (context.actions.isEmpty()) {
				assignLocalActor(context);
			}

			boolean unassignedActorsOnThread = assignActorsToIdleThreads(context);

			Action a = context.actions.poll();
			if (a == null) {
				if (block || unassignedActorsOnThread) {
					if (!announcedAsIdle && block) {
						announcedAsIdle = true;
						// System.out.println("IDLE " +
						// context.thread.getName());
						idleQueue.add(context.thread);
					}
					Thread.sleep(50);
					continue wait_loop;
				}
				return ProcessStatus.EMPTY;
			}
			announcedAsIdle = false;

			// System.out.println("RUN #" + a.hashCode());

			context.actionCount++;
			a.execute();

			return ProcessStatus.PROCESSED;
		}
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
		public String toString() {
			return threadContexts.get(this).toString();
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

	public static void register(ActorRef actor) {
		getThreadContext().instantiationsCount++;
		getThreadContext().instantiations.addFirst(actor);
	}

	public static void statistics() {
		for (ActorThreadContext c : threadContexts.values()) {
			System.out.println(c);
		}
	}
}
