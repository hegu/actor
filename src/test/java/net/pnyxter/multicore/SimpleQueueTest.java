package net.pnyxter.multicore;

import java.util.concurrent.CountDownLatch;

import net.pnyxter.actor.Profiler;
import net.pnyxter.multicore.MulticoreQueue.Follower;

import org.junit.Assert;
import org.junit.Test;

public class SimpleQueueTest {

	private static final int N_THREADS = 10;
	private static final int N_MSG = 20000000;

	@Test
	public void testBasicJdkSimple() {
		testBasic(new JdkSimpleQueue<Integer>());
	}

	@Test
	public void testBasicLinkedUnsafe() {
		testBasic(new UnsafeLinkedQueue<Integer>());
	}

	private void testBasic(final SimpleQueue<Integer> queue) {
		Follower<Integer> f = queue.follower();
		Follower<Integer> f2 = queue.follower();

		queue.add(7);

		Assert.assertEquals(7, f.poll().intValue());

		queue.add(1);
		queue.add(2);
		queue.add(3);

		Assert.assertEquals(1, f.poll().intValue());
		Assert.assertEquals(2, f2.poll().intValue());
		Assert.assertEquals(3, f.poll().intValue());

		Assert.assertNull(f.poll());
		Assert.assertNull(f2.poll());
	}

	@Test
	public void testPerfJdkSimple() throws InterruptedException {
		testPerf(new JdkSimpleQueue<Integer>());
	}

	@Test
	public void testPerfLinkedUnsafe() throws InterruptedException {
		testPerf(new UnsafeLinkedQueue<Integer>());
	}

	private void testPerf(final SimpleQueue<Integer> queue) throws InterruptedException {

		final CountDownLatch done = new CountDownLatch(N_THREADS);

		for (int i = 0; i < N_THREADS; i++) {
			new Thread("Follower-" + i) {
				private final Follower<Integer> f = queue.follower();

				@Override
				public void run() {
					for (;;) {
						Profiler.start();
						Integer polled = f.poll();
						Profiler.add("poll");

						if (polled != null) {

							if (polled.intValue() == 0) {
								done.countDown();
								return;
							}
						} else {
							try {
								Profiler.start();
								Thread.sleep(0L, 1);
								Profiler.add("sleep");
							} catch (InterruptedException e) {
								return;
							}
						}
					}
				}
			}.start();
		}

		for (int i = 0; i < N_THREADS; i++) {
			new Thread("ADDER-" + i) {
				@Override
				public void run() {
					for (int j = 1; j < N_MSG / N_THREADS; j++) {
						Profiler.start();
						queue.add(1);
						Profiler.add("add");
					}
					Profiler.start();
					queue.add(0);
					Profiler.add("add");
				}
			}.start();
		}

		done.await();

		Profiler.print();
	}
}
