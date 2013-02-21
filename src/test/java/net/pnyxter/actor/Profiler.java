package net.pnyxter.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class Profiler {

	private static final CopyOnWriteArrayList<Counters> all = new CopyOnWriteArrayList<>();

	private static class Counters {

		volatile boolean clear = true;

		public Counters() {
			all.add(this);
		}

		volatile Map<String, Long> sums;

		long lastTimestamp;

		public void start() {
			if (clear) {
				sums = new HashMap<>();
				clear = false;
			}
			lastTimestamp = System.nanoTime();
		}

		public void add(String name) {
			long now = System.nanoTime();

			if (sums == null) {
				return;
			}

			Long s = sums.get(name);
			if (s == null) {
				sums.put(name, s = 0L);
			}

			sums.put(name, s.longValue() + (now - lastTimestamp));

			lastTimestamp = now;
		}

	}

	private static final ThreadLocal<Counters> locals = new ThreadLocal<Counters>() {
		@Override
		protected Counters initialValue() {
			return new Counters();
		}
	};

	public static void start() {
		locals.get().start();
	}

	public static void add(String name) {
		locals.get().add(name);
	}

	private static final ArrayList<String> shown = new ArrayList<>();
	private static final HashSet<String> hidden = new HashSet<>();

	public static void show(String name) {
		shown.add(name);
	}

	public static void hide(String name) {
		hidden.add(name);
	}

	public static void sum(String name, String... parts) {

	}

	public static void print() {
		Map<String, Long> totals = new LinkedHashMap<>();

		for (String name : shown) {
			totals.put(name, 0L);
		}

		for (Counters c : all) {
			if (!c.clear) {
				for (Map.Entry<String, Long> e : c.sums.entrySet()) {
					String name = e.getKey();
					Long t = totals.get(name);
					if (t == null) {
						totals.put(name, t = 0L);
					}
					totals.put(name, t + e.getValue().longValue());
				}
			}
		}

		System.out.println("Totals:");
		for (Map.Entry<String, Long> e : totals.entrySet()) {
			if (!hidden.contains(e.getKey())) {
				System.out.println("\t" + e.getKey() + ":\t" + (e.getValue().longValue() / 1000000) + "ms");
			}
		}

		for (Counters c : all) {
			c.clear = true;
		}
	}
}
