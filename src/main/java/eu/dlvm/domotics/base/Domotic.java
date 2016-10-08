package eu.dlvm.domotics.base;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.dlvm.domotics.DriverMonitor;
import eu.dlvm.domotics.server.ServiceServer;
import eu.dlvm.iohardware.ChannelFault;
import eu.dlvm.iohardware.IHardwareIO;

/**
 * Central singleton in domotic system.
 * <p>
 * Overview of methods:
 * <ol>
 * <li>{@link #singleton(IHardwareIO)} creates singleton and accepts hardware
 * driver connection</li>
 * <li>addSensor etc. methods (TODO should be addBlock), to be called first, to
 * construct the domotic system</li>
 * <li>{@link #initialize(Map)} should then be called to do some one-time
 * initialization</li>
 * <li>{@link #runDomotic(int, String, boolean)} will then start the system by
 * calling {@link #loopOnce(long)} regularly, and monitor everything</li>
 * </ol>
 * <p>
 * {@link #requestStop()} will halt domotic system.
 * <p>
 * {@link #loopOnce()} is the key method that drives every input to an output.
 * 
 * @author dirk vaneynde
 * 
 *         TODO veel te veel methodes, opsplitsen - maar hoe? TODO monitoring en
 *         restart werkte niet, weggooien?
 */
public class Domotic implements IDomoticContext {

	public static final int MONITORING_INTERVAL_MS = 5000;
	public static int RESTART_DRIVER_WAITTIME_MS = 30000;

	private static Logger log = LoggerFactory.getLogger(Domotic.class);
	private static Logger MON = LoggerFactory.getLogger("MONITOR");
	private static Domotic singleton;

	private Thread maintThread;
	private DriverMonitor driverMonitor;
	private Process driverProcess;
	private OutputStateSaver saveState;

	private AtomicBoolean stopRequested = new AtomicBoolean();
	private AtomicBoolean restartDriverRequested = new AtomicBoolean();

	// protected access for test cases only
	protected IHardwareIO hw = null;
	protected List<Sensor> sensors = new ArrayList<Sensor>(64);
	protected List<Actuator> actuators = new ArrayList<Actuator>(64);
	protected List<Controller> controllers = new ArrayList<Controller>(64);
	protected List<IStateChangedListener> stateChangeListeners;
	protected long loopSequence = -1L;

	private List<IUiCapableBlock> uiblocks = new ArrayList<IUiCapableBlock>(64);

	public static synchronized Domotic singleton() {
		if (singleton == null) {
			singleton = new Domotic();
		}
		return singleton;
	}

	public static synchronized Domotic singleton(IHardwareIO hw) {
		if (singleton == null) {
			singleton = new Domotic();
			singleton.setHw(hw);
		}
		return singleton;
	}

	public static synchronized void resetSingleton() {
		singleton = null;
	}

	// Forceer singleton
	private Domotic() {
		super();
		saveState = new OutputStateSaver();
		stateChangeListeners = new LinkedList<>();
	}

	public void setHw(IHardwareIO hw) {
		this.hw = hw;
	}

	@Override
	public IHardwareIO getHw() {
		return hw;
	}

	/**
	 * Add Sensor to loop set (see {@link #loopOnce()}.
	 * 
	 * @param sensor
	 *            Added, if not already present. Each Sensor can be present no
	 *            more than once.
	 */
	public void addSensor(Sensor sensor) {
		if (sensors.contains(sensor)) {
			log.warn("Sensor already added, ignored: " + sensor);
			assert (false);
			return;
		}
		sensors.add(sensor);
		log.info("Added sensor " + sensor.getName());
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	/**
	 * Add Actuator to loop set (see {@link #loopOnce()}.
	 * 
	 * @param s
	 *            Added, if not already present. Each Actuator can be present no
	 *            more than once.
	 */
	public void addActuator(Actuator actuator) {
		if (actuators.contains(actuator)) {
			log.warn("Actuator already added, ignored: " + actuator);
			assert (false);
			return;
		}
		actuators.add(actuator);
		log.info("Added actuator " + actuator.getName());
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void addController(Controller controller) {
		if (controllers.contains(controller)) {
			log.warn("Controller already added, ignored: " + controller);
			assert (false);
			return;
		}
		controllers.add(controller);
		log.info("Added controller " + controller.getName());
	}

	public IUiCapableBlock findUiCapable(String name) {
		for (IUiCapableBlock ui : uiblocks) {
			if (ui.getUiInfo().getName().equals(name))
				return ui;
		}
		return null;
	}

	/**
	 * @return all registered {@link Actuator} and {@link Controller} blocks
	 *         that implement {@link IUiCapableBlock}, or those blocks
	 *         registered explicitly...
	 */
	public List<IUiCapableBlock> getUiCapableBlocks() {
		return uiblocks;
	}

	@Override
	public void addStateChangedListener(IStateChangedListener updator) {
		stateChangeListeners.add(updator);
		log.info("Added new UI updator id=" + updator.getId());
	}

	@Override
	public void removeStateChangedListener(IStateChangedListener updator) {
		boolean removed = stateChangeListeners.remove(updator);
		log.info("Removing updator id=" + updator.getId() + " (listener was found and thus removed: " + removed + ")");
	}

	/**
	 * Initializes Domotic system, after all Blocks ({@link Block}) were added.
	 * <p>
	 * Specifically, hardware outputs are set correctly, and UI blocks are
	 * gathered from allready registered blocks.
	 * <p>
	 * Must be called before {@link #loopOnce(long)} or {@link #stop}.
	 * 
	 * @param prevOuts
	 *            Map of actuator names and previous outputs. If not used must
	 *            be empty map (not <code>null</code>).
	 */
	public void initialize(Map<String, RememberedOutput> prevOuts) {
		loopSequence++;
		try {
			hw.initialize();
		} catch (ChannelFault e) {
			log.error("Cannot start Domotic, cannot communicate with driver.");
			throw new RuntimeException("Problem communicating with driver.");
		}
		for (Actuator a : actuators) {
			RememberedOutput ro = prevOuts.get(a.getName());
			a.initializeOutput(ro);
		}
		hw.refreshOutputs();
		for (Block b : sensors)
			registerIfUiCapable(b);
		for (Block b : controllers)
			registerIfUiCapable(b);
		for (Block b : actuators)
			registerIfUiCapable(b);

	}

	private void registerIfUiCapable(Block b) {
		if (b instanceof IUiCapableBlock)
			addUiCapableBlock((IUiCapableBlock) b);
	}

	//@Override
	private void addUiCapableBlock(IUiCapableBlock uiblock0) {
		if (uiblock0.getUiInfo() == null) {
			// TODO all Controllers zijn nu IUserDinges, maar dat is niet juist.
			log.warn("Not adding UI info for " + ((Block) uiblock0).getName()
					+ ". BlockInfo is null - is a bug, refactor code.");
			return;
		}
		for (IUiCapableBlock uiblock : uiblocks) {
			if (uiblock.getUiInfo().getName().equals(uiblock0.getUiInfo().getName())) {
				log.warn("addUiCapableBlock(): incoming UiCapable '" + uiblock0.getUiInfo().getName()
						+ "' already registered - ignored.");
				return;
			}
		}
		uiblocks.add(uiblock0);
		log.debug("Added UiCapableBlock " + uiblock0.getUiInfo().getName());
	}

	private void addShutdownHook(Domotic dom) {
		Runtime.getRuntime().addShutdownHook(new Thread("DomoticShutdownHook") {
			@Override
			public void run() {
				log.info("Inside Add Shutdown Hook");
				stopRequested.set(true);
				log.warn("Stop requested - may take up to 5 seconds...");
			}
		});
		log.info("Shutdown hook attached.");
	}

	/**
	 * Domotic will stop running asap and gracefully. No guarantee...
	 */
	public void requestStop() {
		stopRequested.set(true);

		if (maintThread == null) {
			log.error("Calling interruptMainThread(), but mainThread is not set. Ignored.");
		} else {
			maintThread.interrupt();
			log.info("Interrupted main thread, done by thread=" + Thread.currentThread().getName());
		}
		log.info("Request to stop fulfilled. No guarantee...");
	}

	/**
	 * Runs it.
	 * 
	 * @param looptime
	 * @param pathToDriver
	 *            If non-null, attempts to start the HwDriver executable at that
	 *            path. Note that this driver must be on the same host, since
	 *            'localhost' is passed to it as an argument. Otherwise that
	 *            driver should be started separately, after this one shows
	 *            "START" in the log.
	 * @param checkDriverAndRestartOnError
	 */
	public void runDomotic(int looptime, String pathToDriver, boolean checkDriverAndRestartOnError) {
		addShutdownHook(this);
		this.maintThread = Thread.currentThread();

		ServiceServer server = new ServiceServer();
		server.start(this);

		// TODO see
		// http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html?page=4
		stopRequested.set(false);
		boolean fatalError = false;
		while (!stopRequested.get() && !fatalError) {
			if (pathToDriver != null) {
				fatalError = startDriverAndMonitoring(pathToDriver, fatalError);
				if (fatalError)
					break;
			}

			// TODO is initializatie nodig? probleem is dat ik het nu moet doen
			// want anders wordt TCP connectie niet gelegd
			// Rare is wel dat de lampen blijven branden als er geen connectie
			// is met driver - ik dacht dat dan alles zou uitgaan. Maar ook als
			// driver process weg is blijven lampen branden.
			log.info("Initialize domotic system.");
			initialize(saveState.readRememberedOutputs());

			log.info("Start Domotic thread 'Oscillator'.");
			Oscillator osc = new Oscillator(this, looptime);
			osc.start();

			log.info("Everything started, now watching...");
			long lastLoopSequence = -1;
			while (!stopRequested.get() && !restartDriverRequested.get()) {
				sleepSafe(MONITORING_INTERVAL_MS); // TODO deze sleep moet
													// interrupted ! Of heb ik dat
													// al gedaan?
				saveState.writeRememberedOutputs(getActuators());

				long currentLoopSequence = loopSequence;
				if (currentLoopSequence <= lastLoopSequence) {
					if (checkDriverAndRestartOnError) {
						log.error("Domotic does not seem to be looping anymore, last recorded loopsequence="
								+ lastLoopSequence + ", current=" + currentLoopSequence
								+ ". I'll try to restart driver.");
						break;
					} else {
						log.warn("Domotic does not seem to be looping anymore, last recorded loopsequence="
								+ lastLoopSequence + ", current=" + currentLoopSequence
								+ ". I'll ignore it since flag to restart is not set.");
					}
				}
				lastLoopSequence = currentLoopSequence;
				if (pathToDriver != null && checkDriverAndRestartOnError) {
					if (driverMonitor.everythingSeemsWorking()) {
						MON.info("Checked driver sub-process, seems OK.");
					} else {
						log.error("Something is wrong with driver subprocess. Report:\n" + driverMonitor.report());
						break;
					}
				}
			}
			boolean restartRequested = !stopRequested.get() && !fatalError;
			// shutdown
			stopDriverOscilatorAndMonitor(pathToDriver, osc);
			if (restartRequested) {
				log.info("Will restart driver in " + RESTART_DRIVER_WAITTIME_MS / 1000 + " seconds...");
				sleepSafe(RESTART_DRIVER_WAITTIME_MS);
			}
		}
		server.stop();
		log.info("Domotica run exited.");
	}

	/**
	 * This is what happens:
	 * <ol>
	 * <li>{@link IHardwareIO#refreshInputs()} is called, so that hardware layer
	 * inputs are refreshed.</li>
	 * <li>All registered Sensors have their {@link Sensor#loop()} run to read
	 * input and/or check timeouts etc. This typically triggers Actuators. Same
	 * happens for Controllers.</li>
	 * <li>Then any registered Actuators have their {@link Actuator#loop()}
	 * executed, so they can update hardware output state.</li>
	 * <li>{@link IHardwareIO#refreshOutputs()} is called, so that hardware
	 * layer outputs are updated.</li>
	 * <li>Finally any {@link IStateChangedListener}s are called to update model
	 * state of connected client UIs.
	 * </ol>
	 * 
	 * @param currentTime
	 *            Current time at loopOnce invocation.
	 */
	public synchronized void loopOnce(long currentTime) {
		loopSequence++;
		if (loopSequence % 100 == 0)
			MON.info("loopOnce() start, loopSequence=" + loopSequence + ", currentTime=" + currentTime);
		hw.refreshInputs();
		for (Sensor s : sensors) {
			s.loop(currentTime, loopSequence);
		}
		for (Controller c : controllers) {
			c.loop(currentTime, loopSequence);
		}
		for (Actuator a : actuators) {
			a.loop(currentTime, loopSequence);
		}
		hw.refreshOutputs();

		if (loopSequence % 10 == 0) {
			for (IStateChangedListener uiUpdator : stateChangeListeners)
				uiUpdator.updateUi();
		}

		if (loopSequence % 10 == 0)
			MON.info("loopOnce() done, loopSequence=" + loopSequence + ", currentTime=" + currentTime);
	}

	private boolean startDriverAndMonitoring(String pathToDriver, boolean fatalError) {
		log.info("Start HwDriver, and wait for startup message from driver...");
		ProcessBuilder pb = new ProcessBuilder(pathToDriver, "localhost");
		try {
			driverProcess = pb.start();
		} catch (IOException e) {
			log.error("Cannot start driver as subprocess. Abort startup.", e);
			fatalError = true;
			return fatalError;
		}
		driverMonitor = new DriverMonitor(driverProcess, "hwdriver");
		int maxTries = 5000 / 200;
		int trial = 0;
		while ((trial++ < maxTries) && driverMonitor.driverNotReady()) {
			sleepSafe(200);
		}
		if (trial >= maxTries) {
			log.warn("Couldn't see startup message from HwDriver to be started, but I'll assume it started.");
		} else {
			log.info("Driver started in " + (trial - 1) * 200 / 1000.0 + " seconds.");
		}
		return fatalError;
	}

	private void stopDriverOscilatorAndMonitor(String pathToDriver, Oscillator osc) {
		osc.requestStop();
		// TODO 50 vervangen door tick time variable
		sleepSafe(50);
		// Zend STOP naar driver
		hw.stop();
		if (pathToDriver != null) {
			// Zeker zijn dat STOP verwerkt is
			sleepSafe(500);
			if (driverMonitor.getProcessWatch().isRunning()) {
				log.warn("STOP command to driver did not work, stop forcibly...");
				driverProcess.destroy();
				sleepSafe(500);
				if (driverMonitor.getProcessWatch().isRunning()) {
					log.error("Could not destroy driver process, pid=" + driverMonitor.getProcessWatch().getPid()
							+ ". Ignored, you'll see what happens.");
					// TODO stop domotic?
				}
			} else {
				log.info("Driver stopped, exit code=" + driverMonitor.getProcessWatch().getExitcode()
						+ ". Now Stopping driver monitor.");
			}
			driverMonitor.terminate();
		}
		driverMonitor = null;
		driverProcess = null;
		log.info("Stopped hardware, oscillator and monitor.");
	}

	private static void sleepSafe(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			log.debug("Got interrupted, in Thread.sleep(). ", e);
		}
	}

}
