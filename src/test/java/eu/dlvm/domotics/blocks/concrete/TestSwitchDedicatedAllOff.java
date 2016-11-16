package eu.dlvm.domotics.blocks.concrete;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.dlvm.domotics.actuators.Lamp;
import eu.dlvm.domotics.base.Domotic;
import eu.dlvm.domotics.base.RememberedOutput;
import eu.dlvm.domotics.connectors.IOnOffToggleCapable.ActionType;
import eu.dlvm.domotics.connectors.Switch2OnOffToggle;
import eu.dlvm.domotics.sensors.ISwitchListener.ClickType;
import eu.dlvm.domotics.sensors.Switch;
import junit.framework.Assert;

public class TestSwitchDedicatedAllOff {
	static Logger log = LoggerFactory.getLogger(TestSwitchDedicatedAllOff.class);
	private TestSwitchOrTimer2Lamp.Hardware hw;
	private Domotic dom;
	private Switch swLamp, swAllOff;
	private Lamp lamp;
	private Switch2OnOffToggle swtch2toggle, swtch2All;
	private long cur;

	@Before
	public void init() {
		cur = 0L;

		hw = new TestSwitchOrTimer2Lamp.Hardware();
		hw.in(0, false);
		hw.in(1, false);
		hw.out(10, false);

		Domotic.resetSingleton();
		dom = Domotic.createSingleton(hw);
		swLamp = new Switch("SwitchLamp", "Switch Lamp", Integer.toString(0), dom);
		swAllOff = new Switch("SwitchAllOff", "Switch All Off", Integer.toString(1), dom);
		lamp = new Lamp("Lamp1", "Lamp1", Integer.toString(10), dom);

		swtch2toggle = new Switch2OnOffToggle("toggle", "toggle", null);
		swtch2toggle.map(ClickType.SINGLE, ActionType.TOGGLE);
		swLamp.registerListener(swtch2toggle);
		swtch2toggle.registerListener(lamp);

		swtch2All = new Switch2OnOffToggle("allof", "alloff", null);
		swtch2All.map(ClickType.LONG, ActionType.OFF);
		swAllOff.registerListener(swtch2All);
		swtch2All.registerListener(lamp);
	}

	@Test
	public void testAllOffOk() throws InterruptedException {
		swAllOff.setLongClickEnabled(true);
		swAllOff.setLongClickTimeout(100);
		swAllOff.setDoubleClickEnabled(false);
		swAllOff.setSingleClickEnabled(false);
		// ssb.add(swLamp, lamp);
		// ssb.add(swAllOff, true, false);
		dom.initialize(new HashMap<String, RememberedOutput> (0));

		Assert.assertEquals(false, hw.out(10));
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 60);
		Assert.assertEquals(true, hw.out(10));
		hw.in(1, true);
		dom.loopOnce(cur += 1);
		hw.in(1, false);
		dom.loopOnce(cur += 120);
		Assert.assertEquals(false, hw.out(10));
	}

	@Test
	public void testAllOffTooShort() throws InterruptedException {
		swAllOff.setLongClickEnabled(true);
		swAllOff.setLongClickTimeout(100);
		swAllOff.setDoubleClickEnabled(false);
		swAllOff.setSingleClickEnabled(false);
		// ssb.add(swLamp, lamp);
		// ssb.add(swAllOff, true, false);
		dom.initialize(new HashMap<String, RememberedOutput> (0));

		Assert.assertEquals(false, hw.out(10));
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 60);
		Assert.assertEquals(true, hw.out(10));
		hw.in(1, true);
		dom.loopOnce(cur += 1);
		hw.in(1, false);
		dom.loopOnce(cur += 99);
		Assert.assertEquals(true, hw.out(10));
	}

	@Test
	public void testAllOffTogetherWithNormalSwitchAllOff() throws InterruptedException {
		swAllOff.setLongClickEnabled(true);
		swAllOff.setLongClickTimeout(100);
		swAllOff.setDoubleClickEnabled(false);
		swAllOff.setSingleClickEnabled(false);
		// ssb.add(swAllOff, true, false);
		// ssb.add(swLamp, lamp, true, false);
		dom.initialize(new HashMap<String, RememberedOutput> (0));

		// lamp on
		Assert.assertEquals(false, hw.out(10));
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 60);
		Assert.assertEquals(true, hw.out(10));

		// all off via normal switch
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 120);
		Assert.assertEquals(false, hw.out(10));

		// lamp on again
		Assert.assertEquals(false, hw.out(10));
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 60);
		Assert.assertEquals(true, hw.out(10));

		// lamp off via dedicated switch
		hw.in(1, true);
		dom.loopOnce(cur += 1);
		hw.in(1, false);
		dom.loopOnce(cur += 120);
		Assert.assertEquals(false, hw.out(10));
	}

	@Test
	public void testAllOffTheSameAsNormalSwitchAllOff() throws InterruptedException {
		swLamp.setSingleClickEnabled(true);
		swLamp.setLongClickEnabled(true);
		swLamp.setLongClickTimeout(100);
		swLamp.setDoubleClickEnabled(false);

		swtch2toggle.map(ClickType.LONG, ActionType.OFF);
		dom.initialize(new HashMap<String, RememberedOutput> (0));

		// lamp on
		Assert.assertEquals(false, hw.out(10));
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 60);
		Assert.assertEquals(true, hw.out(10));

		// all off via normal switch
		hw.in(0, true);
		dom.loopOnce(cur += 1);
		hw.in(0, false);
		dom.loopOnce(cur += 120);
		Assert.assertEquals(false, hw.out(10));
	}

}
