package eu.dlvm.domotics.controllers;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.dlvm.domotics.base.Controller;
import eu.dlvm.domotics.base.IDomoticContext;
import eu.dlvm.domotics.connectors.IOnOffToggleCapable;
import eu.dlvm.domotics.connectors.IOnOffToggleCapable.ActionType;
import eu.dlvm.domotics.service.UiInfo;

/**
 * Has two per-day times, at {@link #setOnTime(int, int)} an on signal is sent,
 * at {@link #setOffTime(int, int)} an off signal is sent.
 * <p>
 * Targets {@link IOnOffToggleCapable} listeners.
 * 
 * @author dirk
 */
public class Timer extends Controller {

	private static Logger log = LoggerFactory.getLogger(Timer.class);
	protected int onTime, offTime;
	protected boolean state;
	private Set<IOnOffToggleCapable> listeners = new HashSet<>();

	// helpers
	public static int timeInDay(long time) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);
		int timeInDay = timeInDayMillis(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
		return timeInDay;
	}

	public static int timeInDayMillis(int hour, int minute) {
		return ((hour * 60) + minute) * 60000;
	}

	public static int[] hourMinute(long time) {
		time /= 60000;
		int minute = (int) time % 60;
		int hour = (int) time / 60;
		return new int[] { hour, minute };
	}

	public static long getTimeMsSameDayAtHourMinute(long basetime, int hour, int minute) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(basetime);
		c.set(Calendar.HOUR_OF_DAY, hour);
		c.set(Calendar.MINUTE, minute);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	// timer usage interface
	public Timer(String name, String description, IDomoticContext ctx) {
		super(name, description, null, ctx);
		state = false;
		onTime = offTime = 0;
	}

	public void setOnTime(int hour, int minute) {
		onTime = timeInDayMillis(hour, minute);
	}

	public void setOffTime(int hour, int minute) {
		offTime = timeInDayMillis(hour, minute);
	}

	public boolean isOn() {
		return state;
	}

	// internal usage interface
	public void register(IOnOffToggleCapable listener) {
		listeners.add(listener);
	}

	public void notifyListeners(IOnOffToggleCapable.ActionType action) {
		for (IOnOffToggleCapable l : listeners)
			l.onEvent(action);
	}

	public String getOnTimeAsString() {
		int[] times = hourMinute(onTime);
		return String.format("%02d:%02d", times[0], times[1]);
	}

	public String getOffTimeAsString() {
		int[] times = hourMinute(offTime);
		return String.format("%02d:%02d", times[0], times[1]);
	}

	@Override
	public void loop(long currentTime, long sequence) {
		long currentTimeInDay = timeInDay(currentTime);
		boolean state2 = state;
		if (onTime <= offTime) {
			state2 = (currentTimeInDay > onTime && currentTimeInDay < offTime);
		} else {
			state2 = !(currentTimeInDay > offTime && currentTimeInDay < onTime);
		}
		if (state2 != state) {
			state = state2;
			log.info("Timer '" + getName() + "' sends event '" + (state ? "ON" : "OFF") + "'");
			notifyListeners(state ? ActionType.ON : ActionType.OFF);
		}
	}

	@Override
	public UiInfo getUiInfo() {
		return null;
	}

	@Override
	public void update(String action) {
	}
}
