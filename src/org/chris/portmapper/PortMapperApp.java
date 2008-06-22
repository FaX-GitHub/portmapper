/**
 * 
 */
package org.chris.portmapper;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EventObject;
import java.util.LinkedList;

import javax.swing.JTextArea;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.WriterAppender;
import org.chris.portmapper.gui.PortMapperView;
import org.chris.portmapper.logging.TextAreaWriter;
import org.chris.portmapper.router.Router;
import org.chris.portmapper.router.RouterException;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;

/**
 * @author chris
 * 
 */
public class PortMapperApp extends SingleFrameApplication {

	/**
	 * The name of the system property which will be used as the directory where
	 * all configuration files will be stored.
	 */
	private static final String CONFIG_DIR_PROPERTY_NAME = "portmapper.config.dir";

	/**
	 * The file name for the settings file.
	 */
	private static final String SETTINGS_FILENAME = "settings.xml";

	private Log logger = LogFactory.getLog(this.getClass());

	private Router router;
	private Settings settings;
	private TextAreaWriter logWriter;

	/**
	 * @see org.jdesktop.application.Application#startup()
	 */
	@Override
	protected void startup() {

		initTextAreaLogger();

		setCustomConfigDir();

		loadSettings();

		String useHTMLEncoding = System
				.getProperty("portmapper.settings.use_html_encoding");
		if (useHTMLEncoding != null
				&& useHTMLEncoding.equalsIgnoreCase("false")) {
			this.getSettings().setUseEntityEncoding(false);
			logger
					.info("Do not use html encoding for port mapping description.");
		} else {
			this.getSettings().setUseEntityEncoding(true);
		}

		PortMapperView view = new PortMapperView();
		addExitListener(new ExitListener() {
			public boolean canExit(EventObject arg0) {
				return true;
			}

			public void willExit(EventObject arg0) {
				disconnectRouter();
			}
		});

		show(view);
	}

	/**
	 * Read the system property with name
	 * {@link PortMapperApp#CONFIG_DIR_PROPERTY_NAME} and change the local
	 * storage directory if the property is given and points to a writable
	 * directory.
	 */
	private void setCustomConfigDir() {
		String customConfigurationDir = System
				.getProperty(CONFIG_DIR_PROPERTY_NAME);
		if (customConfigurationDir != null) {
			File dir = new File(customConfigurationDir);
			if (!dir.isDirectory()) {
				logger.error("Custom configuration directory '"
						+ customConfigurationDir + "' is not a directory.");
				System.exit(1);
			}
			if (!dir.canRead() || !dir.canWrite()) {
				logger
						.error("Can not read or write to custom configuration directory '"
								+ customConfigurationDir + "'.");
				System.exit(1);
			}
			logger.info("Using custom configuration directory '"
					+ dir.getAbsolutePath() + "'.");
			getContext().getLocalStorage().setDirectory(dir);
		} else {
			logger.info("Using default configuration directory '"
					+ getContext().getLocalStorage().getDirectory()
							.getAbsolutePath() + "'.");
		}
	}

	/**
	 * Load the application settings from file
	 * {@link PortMapperApp#SETTINGS_FILENAME} located in the configuration
	 * directory.
	 */
	private void loadSettings() {
		logger.info("Loading settings from file " + SETTINGS_FILENAME);
		try {
			settings = (Settings) getContext().getLocalStorage().load(
					SETTINGS_FILENAME);
		} catch (IOException e) {
			logger.warn("Could not load settings from file", e);
		}

		if (settings == null) {
			logger
					.info("Settings were not loaded from file: create new settings");
			settings = new Settings();
		} else {
			logger.info("Got settings " + settings);
		}
	}

	private void initTextAreaLogger() {
		WriterAppender writerAppender = (WriterAppender) Logger.getLogger(
				"org.chris").getAppender("jtextarea");
		logWriter = new TextAreaWriter();
		writerAppender.setWriter(logWriter);
	}

	public void setLoggingTextArea(JTextArea textArea) {
		this.logWriter.setTextArea(textArea);
	}

	@Override
	protected void shutdown() {
		super.shutdown();
		logger.info("Saving settings " + settings + " to file "
				+ SETTINGS_FILENAME);
		try {
			getContext().getLocalStorage().save(settings, SETTINGS_FILENAME);
		} catch (IOException e) {
			logger.warn("Could not save settings to file", e);
		}
	}

	public static PortMapperApp getInstance() {
		return SingleFrameApplication.getInstance(PortMapperApp.class);
	}

	public static ResourceMap getResourceMap() {
		return PortMapperApp.getInstance().getContext().getResourceMap();
	}

	public PortMapperView getView() {
		return (PortMapperView) PortMapperApp.getInstance().getMainView();
	}

	public boolean connectRouter() throws RouterException {
		if (this.router != null) {
			logger
					.warn("Already connected to router. Cannot create a second connection.");
			return false;
		}
		logger.info("Searching for router...");
		this.router = Router.findRouter();
		logger.info("Connected to router " + router.getName());

		boolean isConnected = this.router != null;
		this.getView().fireConnectionStateChange();
		return isConnected;
	}

	/**
	 * @return
	 */
	public boolean disconnectRouter() {
		if (this.router == null) {
			logger.warn("Not connected to router. Can not disconnect.");
			return false;
		}

		this.router.disconnect();
		this.router = null;
		this.getView().fireConnectionStateChange();

		return true;
	}

	public Router getRouter() {
		return router;
	}

	public Settings getSettings() {
		return settings;
	}

	public boolean isConnected() {
		return this.getRouter() != null;
	}

	public String getLocalHostAddress() {
		logger.info("Get IP of localhost");
		InetAddress localHostIP = null;
		try {
			localHostIP = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			logger.error("Could not get IP of localhost", e);
		}

		if (!localHostIP.getHostAddress().startsWith("127.")) {
			return localHostIP.getHostAddress();
		}

		Collection<InetAddress> localHostIPs = new LinkedList<InetAddress>();
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			logger.info("Host name of localhost: " + localHost.getHostName()
					+ ", canonical host name: "
					+ localHost.getCanonicalHostName());
			localHostIPs.addAll(Arrays.asList(InetAddress
					.getAllByName(localHost.getCanonicalHostName())));
			localHostIPs.addAll(Arrays.asList(InetAddress
					.getAllByName(localHost.getHostAddress())));
			localHostIPs.addAll(Arrays.asList(InetAddress
					.getAllByName(localHost.getHostName())));
		} catch (UnknownHostException e) {
			logger.error("Could not get IP of localhost", e);
		}
		for (InetAddress address : localHostIPs) {
			logger.info("Got IP address " + address);
			if (!address.getHostAddress().startsWith("127.")) {
				return address.getHostAddress();
			}
			logger
					.warn("Only found IP addresses starting with '127.'. This is a known issue with some Linux distributions.");
		}

		return null;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		launch(PortMapperApp.class, args);
	}

}
