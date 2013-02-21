package net.pnyxter.multicore;

import java.util.concurrent.ConcurrentLinkedQueue;

public class JdkSimpleQueue<M> extends ConcurrentLinkedQueue<M> implements SimpleQueue<M>, MulticoreQueue.Follower<M> {

	@Override
	public Follower<M> follower() {
		return this;
	}

}
