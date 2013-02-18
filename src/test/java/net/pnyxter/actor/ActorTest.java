package net.pnyxter.actor;

import java.security.SecureRandom;

import net.pnyxter.actor.system.ActorSystem;
import net.pnyxter.actor.system.ActorSystem.ProcessType;

import org.junit.Test;

public class ActorTest {

	public interface Sum {
		void sum(long sum);
	}

	@Actor
	public static class RandomSum implements Sum {

		private final int depth;
		private final int width;

		private final Sum callback;

		private long sum = 0;
		private int expected = 0;

		public RandomSum(int depth, int width, Sum callback) {
			this.depth = depth;
			this.width = width;
			this.callback = callback;
		}

		@Inbox
		public void process() {
			if (depth == 0) {
				callback.sum(new SecureRandom().nextLong());
			} else {
				sum = 0;
				expected = width;

				for (int i = 0; i < width; i++) {
					new RandomSum(depth - 1, width, this).process();
				}
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

		final long start = System.nanoTime();

		// ActorSystem.start(10);

		new RandomSum(3, 3, new Sum() {
			@Override
			public void sum(long sum) {
				System.out.println("Sum: " + sum + " after " + (System.nanoTime() - start) + "ns");
			}
		}).process();

		ActorSystem.process(ProcessType.UNTIL_NO_WORK);
	}

}
