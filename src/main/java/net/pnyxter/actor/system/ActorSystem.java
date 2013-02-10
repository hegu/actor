package net.pnyxter.actor.system;

import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

import net.pnyxter.actor.dispatcher.ActorQueue.Action;

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

	private static LinkedList<Action> queue = new LinkedList<>();

	public static void start(int threads) {

	}

	public static void add(Action a) {
		System.out.println("ADD #" + a.hashCode());
		queue.addFirst(a);
	}

	/**
	 * @param type
	 * @return {@code true} if more work is to be processed
	 */
	public static boolean process(ProcessType type) {
		switch (type) {
		case TRY_SINGLE:
		case WAIT_SINGLE: {
			Action a = queue.pollFirst();
			if (a != null) {
				System.out.println("RUN #" + a.hashCode());
				a.execute();
			} else {
				return false;
			}
			return !queue.isEmpty();
		}
		case UNTIL_NO_WORK:
		case UNTIL_SHUTDOWN:
			for (;;) {
				Action a = queue.pollFirst();
				if (a != null) {
					System.out.println("RUN #" + a.hashCode());
					a.execute();
				} else {
					return false;
				}
			}
		}

		return !queue.isEmpty();
	}
}
