package eu.dlvm.domotics.factories;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import eu.dlvm.domotics.actuators.DimmedLamp;
import eu.dlvm.domotics.actuators.Fan;
import eu.dlvm.domotics.actuators.Lamp;
import eu.dlvm.domotics.actuators.Screen;
import eu.dlvm.domotics.base.Actuator;
import eu.dlvm.domotics.base.Block;
import eu.dlvm.domotics.base.ConfigurationException;
import eu.dlvm.domotics.base.IDomoticContext;
import eu.dlvm.domotics.base.Sensor;
import eu.dlvm.domotics.connectors.Connector;
import eu.dlvm.domotics.controllers.RepeatOffAtTimer;
import eu.dlvm.domotics.controllers.SunWindController;
import eu.dlvm.domotics.controllers.Timer;
import eu.dlvm.domotics.controllers.TimerDayNight;
import eu.dlvm.domotics.controllers.newyear.NewYearBuilder;
import eu.dlvm.domotics.events.EventType;
import eu.dlvm.domotics.events.IEventListener;
import eu.dlvm.domotics.sensors.DimmerSwitch;
import eu.dlvm.domotics.sensors.LightSensor;
import eu.dlvm.domotics.sensors.Switch;
import eu.dlvm.domotics.sensors.WindSensor;

class DomoticXmlDefaultHandler extends DefaultHandler2 {

	static Logger logger = LoggerFactory.getLogger(DomoticXmlDefaultHandler.class);

	private IDomoticContext ctx;
	private int depth = -1; // -1 because domotic startelement makes it start and that is really 0
	private Block currentBlock;
	private String name, desc, ui;
	private String channel, channelDown, channelUp;
	private Map<String, Block> blocksSoFar = new HashMap<>();

	private static class ActionEvent {
		public Block srcBlock;
		public EventType srcEvent;;
	}

	public DomoticXmlDefaultHandler(IDomoticContext ctx) {
		super();
		this.ctx = ctx;
	}

	public void startElement(String uri, String localName, String qqName, Attributes atts) throws SAXException {
		depth++;
		try {
			if (localName.equals("domotic")) {
				;

				// ===== Inner elements

			} else if (localName.equals("on")) {
				if (currentBlock instanceof Timer) {
					((Timer) currentBlock).setOnTime(Integer.parseInt(atts.getValue("hour")), Integer.parseInt(atts.getValue("minute")));
				} else if (currentBlock instanceof Actuator) {
					connectEvent2Action(atts, EventType.ON);
				} else
					throw new RuntimeException("Bug.");

			} else if (localName.equals("off")) {
				if (currentBlock instanceof Timer) {
					((Timer) currentBlock).setOffTime(Integer.parseInt(atts.getValue("hour")), Integer.parseInt(atts.getValue("minute")));
				} else if (currentBlock instanceof IEventListener) {
					connectEvent2Action(atts, EventType.OFF);
				} else
					throw new RuntimeException("Bug.");

			} else if (localName.equals("toggle")) {
				connectEvent2Action(atts, EventType.TOGGLE);

			} else if (localName.equals("delayedOnOff")) {
				IEventListener target = (IEventListener) currentBlock;
				Block source = blocksSoFar.get(atts.getValue("src"));
				source.registerListener(new Connector(EventType.ON, target, EventType.DELAY_ON, "delayOn"));
				source.registerListener(new Connector(EventType.OFF, target, EventType.DELAY_OFF, "delayOff"));
			} else if (localName.equals("wind")) {
				SunWindController controller = (SunWindController) currentBlock;
				WindSensor windSensor = (WindSensor) blocksSoFar.get(atts.getValue("sensor"));
				windSensor.registerListener(controller);
			} else if (localName.equals("sun")) {
				SunWindController controller = (SunWindController) currentBlock;
				LightSensor lightSensor = (LightSensor) blocksSoFar.get(atts.getValue("sensor"));
				lightSensor.registerListener(controller);
			} else if (localName.equals("upDown")) {
				Sensor srcUp = (Sensor) blocksSoFar.get(atts.getValue("srcUp"));
				Sensor srcDown = (Sensor) blocksSoFar.get(atts.getValue("srcDown"));
				String eventName = atts.getValue("event");
				if (eventName == null)
					eventName = "SingleClick";
				srcUp.registerListener(new Connector(EventType.fromAlias(eventName), (Screen) currentBlock, EventType.TOGGLE_UP, "up"));
				srcDown.registerListener(new Connector(EventType.fromAlias(eventName), (Screen) currentBlock, EventType.TOGGLE_DOWN, "down"));
				// ===== Sensors 

			} else if (localName.equals("switch")) {
				parseBaseBlockWithChannel(atts);
				boolean singleClick = parseBoolAttribute("singleClick", true, atts);
				boolean longClick = parseBoolAttribute("longClick", false, atts);
				boolean doubleClick = parseBoolAttribute("doubleClick", false, atts);
				currentBlock = new Switch(name, desc, channel, singleClick, longClick, doubleClick, ctx);

			} else if (localName.equals("dimmerSwitches")) {
				if (currentBlock instanceof DimmedLamp) {
					String dimmerSwitchName = atts.getValue("src");
					DimmerSwitch dimmerSwitch = (DimmerSwitch) blocksSoFar.get(dimmerSwitchName);
					dimmerSwitch.registerListener((DimmedLamp) currentBlock);
				} else {
					parseBaseBlock(atts);
					String s = atts.getValue("channelDown");
					String channelDown = (s == null ? new String(name + "Down") : new String(s));
					s = atts.getValue("channelUp");
					String channelUp = (s == null ? new String(name + "Up") : new String(s));
					currentBlock = new DimmerSwitch(name, desc, channelDown, channelUp, ctx);
				}

			} else if (localName.equals("windSensor")) {
				parseBaseBlockWithChannel(atts);
				int highFreqThreshold = parseIntAttribute("highFreq", atts);
				int lowFreqThreshold = parseIntAttribute("lowFreq", atts);
				int highTimeBeforeAlert = parseIntAttribute("highTimeBeforeAlert", atts);
				int lowTimeToResetAlert = parseIntAttribute("lowTimeToResetAlert", atts);
				currentBlock = new WindSensor(name, desc, ui, channel, ctx, highFreqThreshold, lowFreqThreshold, highTimeBeforeAlert, lowTimeToResetAlert);

			} else if (localName.equals("lightGauge")) {
				parseBaseBlockWithChannel(atts);
				int highThreshold = parseIntAttribute("high", atts);
				int lowThreshold = parseIntAttribute("low", atts);
				int low2highTime = parseIntAttribute("low2highTime", atts);
				int high2lowTime = parseIntAttribute("high2lowTime", atts);
				currentBlock = new LightSensor(name, desc, ui, channel, ctx, lowThreshold, highThreshold, low2highTime, high2lowTime);

				// ===== Controllers 

			} else if (localName.equals("timer")) {
				parseBaseBlock(atts);
				currentBlock = new Timer(name, desc, ctx);

			} else if (localName.equals("timerDayNight")) {
				parseBaseBlock(atts);
				currentBlock = new TimerDayNight(name, desc, ctx);

			} else if (localName.equals("repeatOff")) {
				parseBaseBlock(atts);
				int intervalSec = parseIntAttribute("intervalSec", atts);
				currentBlock = new RepeatOffAtTimer(name, desc, ctx, intervalSec);

			} else if (localName.equals("sunWindController")) {
				parseBaseBlock(atts);
				currentBlock = new SunWindController(name, desc, ui, ctx);

			} else if (localName.equals("newyear")) {
				Date start = DatatypeConverter.parseDateTime(atts.getValue("start")).getTime();
				Date end = DatatypeConverter.parseDateTime(atts.getValue("end")).getTime();
				currentBlock = new NewYearBuilder().build(blocksSoFar, start.getTime(), end.getTime(), ctx);

				// ===== Actuators

			} else if (localName.equals("lamp")) {
				parseBaseBlockWithChannel(atts);
				currentBlock = new Lamp(name, desc, ui, channel, ctx);
			} else if (localName.equals("toggle")) {
				// actuators

			} else if (localName.equals("dimmedLamp")) {
				parseBaseBlockWithChannel(atts);
				int fullOn = Integer.parseInt(atts.getValue("fullOnHwOutput"));
				DimmedLamp dimmedLamp = new DimmedLamp(name, desc, ui, fullOn, channel, ctx);
				currentBlock = dimmedLamp;

			} else if (localName.equals("fan")) {
				parseBaseBlockWithChannel(atts);
				currentBlock = new Fan(name, desc, channel, ctx);

			} else if (localName.equals("screen")) {
				if (currentBlock instanceof SunWindController) {
					SunWindController swc = (SunWindController) currentBlock;
					Screen screen = (Screen) blocksSoFar.get(atts.getValue("name"));
					swc.registerListener(screen);
				} else {
					parseBaseBlockWithUpDownChannel(atts);
					currentBlock = new Screen(name, desc, ui, channelDown, channelUp, ctx);
					if (atts.getValue("motor-up-time") != null)
						((Screen) currentBlock).setMotorUpPeriod(Integer.parseInt(atts.getValue("motor-up-time")));
					if (atts.getValue("motor-dn-time") != null)
						((Screen) currentBlock).setMotorDnPeriod(Integer.parseInt(atts.getValue("motor-dn-time")));
				}
			} else {
				throw new SAXException("Element '" + qqName + "' not supported.");
			}
		} catch (SAXException e) {
			throw e;
		} catch (Exception e) {
			throw new SAXException("Error while processing element '" + localName + "', current block is '"
					+ (currentBlock == null ? "null" : currentBlock.getName()) + "'. Error: '" + e.getMessage() + "'", e);
		}
	}

	public void endElement(String uri, String localName, String qqName) throws SAXException {
		depth--;
		if (depth == 0) {
			blocksSoFar.put(currentBlock.getName(), currentBlock);
			currentBlock = null;

		}
	}

	// ===== Helpers =====

	private void connectEvent2Action(Attributes atts, EventType targetEventType) {
		ActionEvent ae = parseActionEvent(atts);
		Connector c = new Connector(ae.srcEvent, (IEventListener) currentBlock, targetEventType,
				"(" + ae.srcBlock.getName() + ',' + ae.srcEvent + ")_TO_(" + currentBlock.getName() + "," + targetEventType.name() + ")");

		ae.srcBlock.registerListener(c);
	}

	private ActionEvent parseActionEvent(Attributes atts) {
		ActionEvent ae = new ActionEvent();
		String srcName = atts.getValue("src");
		String eventAlias = atts.getValue("event");
		if (eventAlias == null)
			eventAlias = EventType.SINGLE_CLICK.getAlias();

		Block src = blocksSoFar.get(srcName);
		if (src != null) {
			ae.srcBlock = src;
			ae.srcEvent = EventType.fromAlias(eventAlias);
		} else {
			throw new ConfigurationException("Could not find srcBlock =" + srcName + ". Check config of " + currentBlock.getName());
		}
		return ae;
	}

	private void parseBaseBlockWithChannel(Attributes atts) {
		parseBaseBlock(atts);
		String s = atts.getValue("channel");
		if (s == null)
			s = name;
		channel = new String(s);
	}

	private void parseBaseBlockWithUpDownChannel(Attributes atts) {
		parseBaseBlock(atts);
		String s = atts.getValue("channelDown");
		channelDown = (s == null ? new String(name + "Down") : new String(s));
		s = atts.getValue("channelUp");
		channelUp = (s == null ? new String(name + "Up") : new String(s));
	}

	private void parseBaseBlock(Attributes atts) {
		name = atts.getValue("name");
		desc = atts.getValue("desc");
		ui = atts.getValue("ui");
	}

	private boolean parseBoolAttribute(String attName, boolean defaultVal, Attributes atts) {
		if (atts.getValue(attName) == null)
			return defaultVal;
		else
			return Boolean.parseBoolean(atts.getValue(attName));
	}

	private int parseIntAttribute(String attName, Attributes atts) {
		if (atts.getValue(attName) == null)
			return 0;
		else
			return Integer.parseInt(atts.getValue(attName));
	}

}
