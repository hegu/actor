package net.pnyxter.multicore;

import java.util.concurrent.atomic.AtomicReference;

public class VolatileBroadcastQueue<M> implements BroadcastQueue<M> {

	private static class Node<M> {

		private final AtomicReference<Node<M>> next = new AtomicReference<>();

		private final M message;

		public Node(M message) {
			this.message = message;
		}

		M get() {
			return message;
		}
	}

	private volatile Node<M> head = new Node<>(null);

	@Override
	public void add(M message) {
		Node<M> added = new Node<>(message);

		Node<M> cursor = head;

		for (;;) {
			if (cursor.next.compareAndSet(null, added)) {
				head = added;
				return;
			}
			cursor = cursor.next.get();
		}
	}

	@Override
	public Follower<M> follower() {
		return new Follower<M>() {

			Node<M> cursor = head;

			@Override
			public M poll() {
				Node<M> valueNode = cursor.next.get();
				if (valueNode == null) {
					return null;
				}
				cursor = valueNode;
				return valueNode.get();
			}
		};
	}

}
