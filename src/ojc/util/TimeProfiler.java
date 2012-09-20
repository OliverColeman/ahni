package ojc.util;

import java.util.HashMap;
import java.util.Vector;

public class TimeProfiler {
	static private HashMap<String, Long> times = new HashMap<String, Long>();
	static private HashMap<String, Vector<Long>> startTimes = new HashMap<String, Vector<Long>>();
	static private long total;

	public synchronized static void addTime(String id, long t) {
		if (times.get(id) == null)
			times.put(id, new Long(t));
		else
			times.put(id, new Long(t + getTime(id)));

		total += t;
	}

	public static long getTime(String id) {
		if (times.get(id) != null)
			return times.get(id).longValue();
		else
			return 0;
	}

	public static float getTimeAsSeconds(String id) {
		return (float) getTime(id) / 1000;
	}

	public static long getTotal() {
		return total;
	}

	public static float getTotalAsSeconds(String id) {
		return (float) total / 1000;
	}

	public static double getProportion(String id) {
		return (double) getTime(id) / total;
	}

	public static void markStart(String id) {
		markStart(id, 0);
	}
	public synchronized static void markStart(String id, int pos) {
		//System.out.println("Set start time for " + id + ":" + pos);
		
		Long startTime = System.currentTimeMillis();
		Vector<Long> st = startTimes.get(id);
		if (st == null) {
			st = new Vector<Long>();
			startTimes.put(id, st);
		}
		if (pos < st.size() && st.get(pos) != null) {
			System.out.println("Start time already set for " + id + ":" + pos);
			return;
		}
		if (pos >= st.size())
			st.setSize(pos+1);
		st.set(pos, new Long(startTime));
	}
	
	public static void markFinish(String id) {
		markFinish(id, 0);
	}
	public synchronized static void markFinish(String id, int pos) {
		//System.out.println("Set finish time for " + id + ":" + pos);
		
		Long endTime = System.currentTimeMillis();
		Vector<Long> st = startTimes.get(id);
		if (st == null || pos >= st.size() || st.get(pos) == null) {
			System.out.println("Start time not set for " + id + ":" + pos);
			return;
		}
		addTime(id, endTime - st.get(pos).longValue());
		st.set(pos, null);
	}
}
