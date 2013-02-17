package net.pnyxter.multicore;

/**
 * Multiple producers are adding messages to the queue in a thread safe way.
 * Messages are picked using a {@link Follower} that may only be accessed from a
 * single thread.
 * 
 * Null values is not allowed.
 */
public interface BroadcastQueue<M> {

	public interface Follower<M> {
		/**
		 * Poll message from this queue. Return {@code null} if empty. Reading
		 * from tail.
		 * 
		 * @return
		 */
		M poll();
	}

	/**
	 * Return follower initial pointing to the current head of the queue. To
	 * create a follower should be thread safe but access to the follower is not
	 * thread safe.
	 * 
	 * @return
	 */
	Follower<M> follower();

	/**
	 * Add message to head.
	 * 
	 * @param message
	 */
	void add(M message);

}
