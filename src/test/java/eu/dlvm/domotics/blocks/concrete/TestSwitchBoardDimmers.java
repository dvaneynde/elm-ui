package eu.dlvm.domotics.blocks.concrete;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import eu.dlvm.domotics.actuators.DimmedLamp;
import eu.dlvm.domotics.base.Domotic;
import eu.dlvm.domotics.base.RememberedOutput;
import eu.dlvm.domotics.blocks.BaseHardwareMock;
import eu.dlvm.domotics.connectors.DimmerSwitch2Dimmer;
import eu.dlvm.domotics.connectors.Switch2OnOffToggle;
import eu.dlvm.domotics.connectors.IOnOffToggleCapable.ActionType;
import eu.dlvm.domotics.sensors.DimmerSwitch;
import eu.dlvm.domotics.sensors.Switch;
import eu.dlvm.domotics.sensors.ISwitchListener.ClickType;
import eu.dlvm.iohardware.IHardwareIO;
import eu.dlvm.iohardware.LogCh;

public class TestSwitchBoardDimmers {

	static Logger log = Logger.getLogger(TestSwitchBoardDimmers.class);

	public static int LONGCLICKTIMEOUT = 50;
	public static final int FULL_OUT_VAL = 1024;
	public static final LogCh SW_DN_1 = new LogCh(0);
	public static final LogCh SW_UP_1 = new LogCh(1);
	public static final LogCh SW_DN_2 = new LogCh(2);
	public static final LogCh SW_UP_2 = new LogCh(3);
	public static final LogCh SW_ALL = new LogCh(4);
	public static final LogCh DIMMER1 = new LogCh(10);
	public static final LogCh DIMMER2 = new LogCh(11);

	public static class Hardware extends BaseHardwareMock implements IHardwareIO {
		private Map<LogCh, Boolean> inputs = new HashMap<LogCh, Boolean>();
		private Map<LogCh, Integer> outputs = new HashMap<LogCh, Integer>();

		@Override
		public void writeAnalogOutput(LogCh channel, int value) throws IllegalArgumentException {
			outputs.put(channel, value);
		}

		@Override
		public boolean readDigitalInput(LogCh channel) {
			return inputs.get(channel);
		}
	};

	private Domotic dom;
	private Hardware hw;
	private DimmerSwitch dsw1;
	private DimmedLamp dl1;
	private Switch swAllOnOff;
	private DimmerSwitch2Dimmer ds2d;
	private long cur;

	@BeforeClass
	public static void initOnce() {
		BasicConfigurator.configure();
	}

	@Before
	public void init() {
		cur = 0L;
		
		hw = new Hardware();
		hw.inputs.put(SW_DN_1, false);
		hw.inputs.put(SW_UP_1, false);
		hw.inputs.put(SW_DN_2, false);
		hw.inputs.put(SW_UP_2, false);
		hw.inputs.put(SW_ALL, false);
		hw.outputs.put(DIMMER1, 0);
		hw.outputs.put(DIMMER2, 0);

		Domotic.resetSingleton();
		dom = Domotic.singleton(hw);
		dsw1 = new DimmerSwitch("dsw1", "Dimmer Switches 1", SW_DN_1, SW_UP_1, dom);
		dl1 = new DimmedLamp("dl1", "Dimmed Lamp 1", FULL_OUT_VAL, DIMMER1, dom);
		dl1.setMsTimeFullDim(3000);
		
		ds2d = new DimmerSwitch2Dimmer("ds2d", "ds2d");
		ds2d.setLamp(dl1);
		dsw1.registerListener(ds2d);
		
		swAllOnOff = new Switch("swAll", "Switch All On/Off", SW_ALL, dom);
		swAllOnOff.setDoubleClickEnabled(true);
		swAllOnOff.setDoubleClickTimeout(400L);
		swAllOnOff.setLongClickEnabled(true);
		swAllOnOff.setLongClickTimeout(1000L);
		swAllOnOff.setSingleClickEnabled(false);
		Switch2OnOffToggle swtch2AllOff = new Switch2OnOffToggle("allOff", "allOff", null);
		swtch2AllOff.map(ClickType.LONG, ActionType.OFF);
		swAllOnOff.registerListener(swtch2AllOff);
		swtch2AllOff.registerListener(dl1);
	}

	@Test
	public void testFullThenAllOff() {
		// Initialisatie
		dom.initialize(new HashMap<String, RememberedOutput> (0));
		Assert.assertEquals(FULL_OUT_VAL/2, hw.outputs.get(DIMMER1).intValue());
		// Donker
		dl1.on(0);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(0, hw.outputs.get(DIMMER1).intValue());
		// Volledig aan (links down, dan rechts klik)
		hw.inputs.put(SW_DN_1, true);
		dom.loopOnce(cur += 1);
		hw.inputs.put(SW_UP_1, true);
		dom.loopOnce(cur += 1);
		hw.inputs.put(SW_UP_1, false);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(FULL_OUT_VAL, hw.outputs.get(DIMMER1).intValue());
		hw.inputs.put(SW_DN_1, false);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(FULL_OUT_VAL, hw.outputs.get(DIMMER1).intValue());
		// Alles uit
		hw.inputs.put(SW_ALL, true);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(FULL_OUT_VAL, hw.outputs.get(DIMMER1).intValue());
		dom.loopOnce(cur += (swAllOnOff.getLongClickTimeout() + 1));
		Assert.assertEquals(0, hw.outputs.get(DIMMER1).intValue());
		hw.inputs.put(SW_ALL, false);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(0, hw.outputs.get(DIMMER1).intValue());
	}

	@Test
	public void testDimUpAndDownDimmer1() {
		// Initialisatie
		dom.initialize(new HashMap<String, RememberedOutput> (0));
		Assert.assertEquals(FULL_OUT_VAL/2, hw.outputs.get(DIMMER1).intValue());
		// Donker
		dl1.on(0);
		dom.loopOnce(cur += 1);
		Assert.assertEquals(0, hw.outputs.get(DIMMER1).intValue());
		// Dimmen
		int level1, level2;
		dom.loopOnce(cur += 1);
		Assert.assertEquals(0, hw.outputs.get(DIMMER1).intValue());
		dom.loopOnce(cur += 1);
		// go up...
		hw.inputs.put(SW_UP_1, true);
		dom.loopOnce(cur += 1);
		level1 = hw.outputs.get(DIMMER1).intValue();
		Assert.assertEquals(0, level1);
		dom.loopOnce(cur += (dsw1.getClickedTimeoutMS() + 1));
		level1 = hw.outputs.get(DIMMER1).intValue();
		log.debug("level1 = " + level1); // TODO zou toch bijna 0 moeten zijn?
		dom.loopOnce(cur += (dl1.getMsTimeFullDim() / 3));
		level2 = hw.outputs.get(DIMMER1).intValue();
		log.debug("level 1=" + level1 + ", level2=" + level2);
		Assert.assertTrue(level2 > level1);
		// stop going up
		hw.inputs.put(SW_UP_1, false);
		dom.loopOnce(cur += 1);
		level1 = hw.outputs.get(DIMMER1).intValue();
		dom.loopOnce(cur += (dl1.getMsTimeFullDim() / 3));
		level2 = hw.outputs.get(DIMMER1).intValue();
		log.debug("should be equal: level 1=" + level1 + ", level2=" + level2);
		Assert.assertTrue(level1 == level2);
	}
}
