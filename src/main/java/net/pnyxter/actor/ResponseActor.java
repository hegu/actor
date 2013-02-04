package net.pnyxter.actor;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

// XXX: Is this an actor
@Actor
public class ResponseActor<V> {

	private final CountDownLatch done = new CountDownLatch(1);
	private final AtomicReference<V> value = new AtomicReference<>(null);

	@Inbox
	public void response(V value) {

	}

	/**
	 * Returns <tt>true</tt> if response is received.
	 * 
	 * @return <tt>true</tt> if response is received
	 */
	public boolean isDone() {
		return done.getCount() <= 0;
	}

	/**
	 * Waits if necessary for the response to be received, and then retrieves
	 * its value.
	 * 
	 * @return the computed result
	 * @throws CancellationException
	 *             if the computation was cancelled
	 * @throws ExecutionException
	 *             if the computation threw an exception
	 * @throws InterruptedException
	 *             if the current thread was interrupted while waiting
	 */
	public V get() throws InterruptedException, ExecutionException {
		done.await();
		return value.get();
	}

	/**
	 * Waits if necessary for at most the given time for the computation to
	 * complete, and then retrieves its result, if available.
	 * 
	 * @param timeout
	 *            the maximum time to wait
	 * @param unit
	 *            the time unit of the timeout argument
	 * @return the computed result
	 * @throws CancellationException
	 *             if the computation was cancelled
	 * @throws ExecutionException
	 *             if the computation threw an exception
	 * @throws InterruptedException
	 *             if the current thread was interrupted while waiting
	 * @throws TimeoutException
	 *             if the wait timed out
	 */
	public V get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		done.await(timeout, unit);
		return value.get();

	}
}
