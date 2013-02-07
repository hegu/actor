package net.pnyxter.actor;

import org.junit.Test;

public class ActorTest {

	interface Subscriber {
		void currentValue(Integer value);
	}

	@Actor
	class SubscriberActor implements Subscriber {
		@Override
		@Inbox
		public void currentValue(Integer value) {
			System.out.println("Current value: " + value);
		}
	}

	@Actor
	class TestActor {

		private int counter;
		private Subscriber subscriber;

		@Inbox
		public void increase() {
			++counter;
			if (subscriber != null) {
				subscriber.currentValue(counter);
			}

		}

		@Inbox
		public void register(Subscriber s) {
			this.subscriber = s;
			s.currentValue(counter);
		}
	}

	@Test
	public void testCallToActor() {

		TestActor t = new TestActor();
		t.register(new SubscriberActor());

		t.increase();
		t.increase();
		t.increase();

	}

}
