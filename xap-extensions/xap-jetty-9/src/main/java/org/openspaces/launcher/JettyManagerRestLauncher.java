package org.openspaces.launcher;

import com.gigaspaces.logger.Constants;
import com.gigaspaces.lrmi.nio.filters.SelfSignedCertificate;
import com.gigaspaces.start.SystemInfo;
import com.gigaspaces.start.manager.XapManagerClusterInfo;
import com.gigaspaces.start.manager.XapManagerConfig;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import java.io.Closeable;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Yohana Khoury
 * @since 12.1
 */
public class JettyManagerRestLauncher implements Closeable {
    private static final Logger logger = Logger.getLogger(Constants.LOGGER_MANAGER);

    private AbstractXmlApplicationContext application;
    private Server server;

    public static void main(String[] args) {
        final JettyManagerRestLauncher starter = new JettyManagerRestLauncher();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                starter.close();
            }
        });
    }

    /**
     * NOTE: This ctor is also called via reflection from SystemConfig
     */
    @SuppressWarnings("WeakerAccess")
    public JettyManagerRestLauncher() {
        try {
            final XapManagerClusterInfo managerClusterInfo = SystemInfo.singleton().getManagerClusterInfo();
            final XapManagerConfig config = managerClusterInfo.getServers().length != 0
                    ? managerClusterInfo.findManagerByCurrHost()
                    : new XapManagerConfig(SystemInfo.singleton().network().getHostId());
            if (config == null) {
                logger.severe("Cannot start server  - this host is not part of the xap managers configuration");
                System.exit(1);
            }
            String customJettyPath = System.getProperty("com.gs.manager.rest.jetty.config");
            if (customJettyPath != null) {
                logger.info("Loading jetty configuration from " + customJettyPath);
                this.application = new FileSystemXmlApplicationContext(customJettyPath);
                this.server = this.application.getBean(Server.class);
            } else {
                this.server = new Server();
            }
            if (!server.isStarted()) {
                if (server.getConnectors() == null || server.getConnectors().length == 0) {
                    initConnectors(server, config);
                }
                if (server.getHandler() == null) {
                    initWebApps(server);
                }
                server.start();
            }
            if (logger.isLoggable(Level.INFO)) {
                Collection<String> webAppsPaths = Collections.singletonList("v1");
                for (String webAppsPath : webAppsPaths) {
                    String connectors = "";
                    for (Connector connector : server.getConnectors()) {
                        if (connector instanceof ServerConnector) {
                            String connectorDesc = JettyUtils.toUrlPrefix((ServerConnector) connector)  + "/" + webAppsPath;
                            connectors = connectors.isEmpty() ? connectorDesc : connectors + ", " + connectorDesc;
                        }
                    }
                    logger.info("Started at " + connectors);
                }
            }
        }catch(Exception e){
            logger.log(Level.SEVERE, e.toString(), e);
            System.exit(-1);
        }
    }

    private void initConnectors(Server server, XapManagerConfig config)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        final String host = config.getHost();
        final String portsConfig = config.getAdminRest() != null ? config.getAdminRest() : "8080:8443";
        String[] ports = portsConfig.split(":");
        if (ports.length != 2)
            throw new IllegalArgumentException("Invalid rest configuration [" + portsConfig + "] - must be port:sslport");
        if (!ports[0].equalsIgnoreCase("none")) {
            JettyUtils.createConnector(server, host, Integer.parseInt(ports[0]), null);
        }
        if (!ports[1].equalsIgnoreCase("none")) {
            JettyUtils.createConnector(server, host, Integer.parseInt(ports[1]), getOrCreateSslContextFactory());
        }
    }

    private void initWebApps(Server server) {
        ContextHandlerCollection handler = new ContextHandlerCollection();
        File webApps = new File(SystemInfo.singleton().locations().getLibPlatform() + "/admin-rest/webapps");
        FilenameFilter warFilesFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".war");
            }
        };
        for (File file : webApps.listFiles(warFilesFilter)) {
            WebAppContext webApp = new WebAppContext();
            webApp.setContextPath("/" + file.getName().replace(".war", ""));
            webApp.setWar(file.getAbsolutePath());
            webApp.setThrowUnavailableOnStartupException(true);
            handler.addHandler(webApp);
        }
        server.setHandler(handler);
    }

    private SslContextFactory getOrCreateSslContextFactory()
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException {
        SslContextFactory sslContextFactory = new SslContextFactory();
        String keyStorePath = System.getProperty("com.gs.manager.rest.keystore.path");
        String password = System.getProperty("com.gs.manager.rest.keystore.password");

        if (keyStorePath != null && new File(keyStorePath).exists()) {
            sslContextFactory.setKeyStorePath(keyStorePath);
            sslContextFactory.setKeyStorePassword(password);
        } else {
            sslContextFactory.setKeyStore(SelfSignedCertificate.keystore());
            sslContextFactory.setKeyStorePassword("foo");
        }

        return sslContextFactory;
    }

    @Override
    public void close() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                logger.warning("Failed to stop server: " + e);
            }
        }
        if (this.application != null)
            this.application.destroy();
    }
}