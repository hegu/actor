package net.pnyxter.actor;

import java.util.concurrent.CountDownLatch;

import net.pnyxter.actor.system.ActorSystem;
import net.pnyxter.actor.system.ActorSystem.ProcessType;

import org.junit.Test;

public class ActorTest {

	public interface Sum {
		void sum(long sum);
	}

	@Actor
	public static class FibonacciProblem implements Sum {

		private final long n;

		private final Sum callback;

		private long sum = 0;
		private int expected = 2;

		public FibonacciProblem(long n, Sum callback) {
			this.n = n;
			this.callback = callback;
		}

		@Inbox
		public void process() {
			if (n <= 1) {
				callback.sum(n);
			} else {
				new FibonacciProblem(n - 1, this).process();
				new FibonacciProblem(n - 2, this).process();
			}
		}

		@Override
		@Inbox
		public void sum(long sum) {
			expected--;

			this.sum += sum;

			if (expected == 0) {
				callback.sum(this.sum);
			}
		}
	}

	@Test
	public void testCallToActor() throws InterruptedException {

		final CountDownLatch resultComplete = new CountDownLatch(1);
		// ActorSystem.start(1);

		final long start = System.nanoTime();

		new FibonacciProblem(30, new Sum() {
			@Override
			public void sum(long sum) {
				System.out.println("Sum: " + sum + " after " + (System.nanoTime() - start) / 1000000 + "ms");
				resultComplete.countDown();
			}
		}).process();

		ActorSystem.process(ProcessType.UNTIL_SHUTDOWN);

		resultComplete.await();

		ActorSystem.statistics();
	}

}
