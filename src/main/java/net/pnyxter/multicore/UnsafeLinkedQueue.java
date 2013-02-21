package net.pnyxter.multicore;

import java.lang.reflect.Field;

public class UnsafeLinkedQueue<M> implements SimpleQueue<M>, MulticoreQueue.Follower<M> {

	static final sun.misc.Unsafe UNSAFE;

	static {
		try {
			Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			UNSAFE = (sun.misc.Unsafe) field.get(null);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private static class Node<M> {

		private static final long nextOffset;

		static {
			try {
				nextOffset = UNSAFE.objectFieldOffset(Node.class.getDeclaredField("next"));
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}

		private Node<M> next;
		private final M message;

		public Node(M message) {
			this.message = message;
		}
	}

	private static final long headOffset;
	private static final long cursorOffset;

	static {
		try {
			headOffset = UNSAFE.objectFieldOffset(UnsafeLinkedQueue.class.getDeclaredField("head"));
			cursorOffset = UNSAFE.objectFieldOffset(UnsafeLinkedQueue.class.getDeclaredField("cursor"));
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private Node<M> head = new Node<>(null);
	private Node<M> cursor = head;

	@Override
	public boolean add(M message) {
		Node<M> added = new Node<>(message);
		Node<M> h = head;
		Node<M> p = h;

		insert_loop: for (;;) {
			if (UNSAFE.compareAndSwapObject(p, Node.nextOffset, null, added)) {
				break insert_loop;
			}
			Node<M> n = p.next;
			if (p == n) {
				p = cursor;
			} else {
				p = n;
			}
		}

		UNSAFE.compareAndSwapObject(this, headOffset, h, added);

		return true;
	}

	@Override
	public Follower<M> follower() {
		return this;
	}

	@Override
	public M poll() {
		for (;;) {
			Node<M> c = cursor;
			Node<M> valueNode = c.next;

			if (valueNode == null) {
				return null;
			}

			if (UNSAFE.compareAndSwapObject(this, cursorOffset, c, valueNode)) {
				UNSAFE.compareAndSwapObject(c, Node.nextOffset, valueNode, c);
				return valueNode.message;
			}
		}
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

			c = c.next;

			if (c != null) {
				buffer.append(c.message);
			} else {
				break list_loop;
			}
		}

		buffer.append("]");
		return buffer.toString();
	}
}
