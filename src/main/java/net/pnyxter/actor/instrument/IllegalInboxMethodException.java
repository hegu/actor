package net.pnyxter.actor.instrument;

public class IllegalInboxMethodException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public IllegalInboxMethodException(String message) {
		super(message);
	}
}
