package net.pnyxter.multicore;

public class SynchronizedBroadcastQueue<M> implements BroadcastQueue<M> {

	private static class Node<M> {

		private Node<M> next = null;

		private final M message;

		public Node(M message) {
			this.message = message;
		}

		M get() {
			return message;
		}
	}

	private Node<M> head = new Node<>(null);

	@Override
	public synchronized void add(M message) {
		head = head.next = new Node<>(message);
	}

	@Override
	public synchronized Follower<M> follower() {
		return new Follower<M>() {

			Node<M> cursor = head;

			@Override
			public M poll() {
				synchronized (SynchronizedBroadcastQueue.this) {
					if (cursor.next == null) {
						return null;
					}
					cursor = cursor.next;
					return cursor.get();
				}
			}
		};
	}

}
