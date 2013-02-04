package net.pnyxter.actor;

public interface Supervisor<A extends Object, E extends Throwable> {

	/**
	 * 
	 * @return {@code true} if the failure was handled and does not need to be
	 *         propagated.
	 */
	boolean actorFailure(A actor, E exception);
}
