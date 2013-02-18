package net.pnyxter.multicore;

import java.lang.reflect.Field;

public class UnsafeBroadcastQueue<M> implements BroadcastQueue<M> {

	static final sun.misc.Unsafe unsafe;

	static {
		try {
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			unsafe = (sun.misc.Unsafe) field.get(null);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static class Node<M> {

		private static final long nextOffset;

		static {
			try {
				nextOffset = unsafe.objectFieldOffset(Node.class.getDeclaredField("next"));
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}

		private Node<M> next = null;

		private final M message;

		public Node(M message) {
			this.message = message;
		}

		M get() {
			return message;
		}
	}

	private static final long headOffset;

	static {
		try {
			headOffset = unsafe.objectFieldOffset(UnsafeBroadcastQueue.class.getDeclaredField("head"));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private Node<M> head = new Node<>(null);

	@Override
	public void add(M message) {
		Node<M> added = new Node<>(message);

		Node<M> cursor = head;

		for (;;) {
			if (unsafe.compareAndSwapObject(cursor, Node.nextOffset, null, added)) {
				head = added;
				return;
			}
			cursor = (Node<M>) unsafe.getObject(cursor, Node.nextOffset);
		}
	}

	@Override
	public Follower<M> follower() {
		return new Follower<M>() {

			Node<M> cursor = head;

			@Override
			public M poll() {
				@SuppressWarnings("unchecked")
				Node<M> valueNode = (Node<M>) unsafe.getObject(cursor, Node.nextOffset);
				if (valueNode == null) {
					return null;
				}
				cursor = valueNode;
				return valueNode.get();
			}

			@Override
			public String toString() {
				StringBuilder buffer = new StringBuilder();
				buffer.append("[");

				boolean first = true;
				Node<M> c = cursor;
				list_loop: while (c != null) {
					if (!first) {
						buffer.append(',');
					} else {
						first = false;
					}

					c = (Node<M>) unsafe.getObject(c, Node.nextOffset);

					if (c != null) {
						buffer.append(c.message);
					} else {
						break list_loop;
					}
				}

				buffer.append("]");
				return buffer.toString();
			}
		};
	}

	@Override
	public String toString() {
		return "BroadcastQueue";
	}
}
