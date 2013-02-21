package net.pnyxter.multicore;

import java.util.concurrent.CountDownLatch;

import net.pnyxter.multicore.MulticoreQueue.Follower;

import org.junit.Assert;
import org.junit.Test;

public class BroadcastQueueTest {

	private static final int N_THREADS = 10;
	private static final int N_MSG = 10000000;

	@Test
	public void testBasicSynchronized() {
		testBasic(new SynchronizedBroadcastQueue<Integer>());
	}

	@Test
	public void testBasicVolatile() {
		testBasic(new VolatileBroadcastQueue<Integer>());
	}

	@Test
	public void testBasicUnsafe() {
		testBasic(new UnsafeBroadcastQueue<Integer>());
	}

	private void testBasic(final BroadcastQueue<Integer> queue) {
		Follower<Integer> f = queue.follower();
		Follower<Integer> f2 = queue.follower();

		queue.add(7);

		Assert.assertEquals(7, f.poll().intValue());

		queue.add(1);
		queue.add(2);
		queue.add(3);

		Assert.assertEquals(1, f.poll().intValue());
		Assert.assertEquals(2, f.poll().intValue());
		Assert.assertEquals(3, f.poll().intValue());

		// F2
		Assert.assertEquals(7, f2.poll().intValue());
		Assert.assertEquals(1, f2.poll().intValue());
		Assert.assertEquals(2, f2.poll().intValue());
		Assert.assertEquals(3, f2.poll().intValue());

		Assert.assertNull(f.poll());
		Assert.assertNull(f2.poll());
	}

	@Test
	public void testPerfSynchronized() throws InterruptedException {
		testPerf(new SynchronizedBroadcastQueue<Integer>());
	}

	@Test
	public void testPerfVolatile() throws InterruptedException {
		testPerf(new VolatileBroadcastQueue<Integer>());
	}

	@Test
	public void testPerfUnsafe() throws InterruptedException {
		testPerf(new UnsafeBroadcastQueue<Integer>());
	}

	private void testPerf(final BroadcastQueue<Integer> queue) throws InterruptedException {

		final CountDownLatch done = new CountDownLatch(N_THREADS);

		for (int i = 0; i < N_THREADS; i++) {
			new Thread("Follower-" + i) {
				private final Follower<Integer> f = queue.follower();

				@Override
				public void run() {
					int expected = N_MSG;
					for (;;) {
						if (f.poll() != null) {
							expected--;

							if (expected == 0) {

								done.countDown();
								return;
							}
						} else {
							try {
								Thread.sleep(0L, 1);
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
					for (int j = 0; j < N_MSG / N_THREADS; j++) {
						queue.add(j % 17);
					}
				}
			}.start();
		}

		done.await();
	}

}
