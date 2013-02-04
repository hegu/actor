package net.pnyxter.actor.instrument;

import java.lang.instrument.Instrumentation;

public class ActorAgent {
	public static void premain(String agentArguments, Instrumentation instrumentation) {
		ActorClassTransformer transformer = new ActorClassTransformer();
		instrumentation.addTransformer(transformer);
	}
}
