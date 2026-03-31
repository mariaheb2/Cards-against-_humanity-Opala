package cards_against_humanity.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class ServerConfig{

    private static final Logger LOGGER = Logger.getLogger(ServerConfig.class.getName());

    // Classpath location of the properties file
    private static final String CONFIG_FILE = "config/config.properties";

    // Default Fields
    public static final int DEFAULT_PORT = 8080;
    public static final int DEFAULT_MAX_CONNECTIONS = 50;
    public static final int DEFAULT_THREAD_POOL_SIZE = 50;
    public static final int DEFAULT_BACKLOG = 50;
    public static final String DEFAULT_CHARSET = "UTF-8";

    // Instance Fields
    private final int port;
    private final int maxConnection;
    private final int threadPoolSize;
    private final int backlog;
    private final String charset;

    // Loads configuration
    public ServerConfig(){
        Properties props = loadProperties();
        this.port = parseInt(props, "server.port", DEFAULT_PORT);
        this.maxConnection = parseInt(props, "server.max_connections", DEFAULT_MAX_CONNECTIONS);
        this.threadPoolSize = parseInt(props, "server.thread_pool_size", DEFAULT_THREAD_POOL_SIZE);
        this.backlog = parseInt(props, "server.backlog", DEFAULT_BACKLOG);
        this.charset = props.getProperty("server.charset", DEFAULT_CHARSET);
        validate();
    }

    private void validate(){
        if (port < 0 || port > 65535){
            throw new IllegalArgumentException("Port must be in [0 - 65535], got: " + port);
        }
        if (maxConnection < 1){
            throw new IllegalArgumentException("maxConnections must be >=1");
        }
        if (threadPoolSize < 1){
            throw new IllegalArgumentException("threadPoolSize must be >= 1");
        }
        if (backlog < 1){
            throw new IllegalArgumentException("backlog must be >= 1");
        }

    }

    @Deprecated
    public static final String CHARSET = DEFAULT_CHARSET;

    @Override
    public String toString(){
        return "ServerConfig{" +
                "port=" + port +
                ", maxConnection=" + maxConnection +
                ", threadPoolSize=" + threadPoolSize +
                ", backlog=" + backlog +
                ", charset='" + charset + '\'' +
                '}';
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream is = ServerConfig.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                LOGGER.warning("Config file not found on classpath: "
                        + CONFIG_FILE + " — using defaults.");
                return props;
            }
            props.load(is);
            LOGGER.info("Loaded server configuration from " + CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read " + CONFIG_FILE + " — using defaults.", e);
        }
        return props;
    }

    private static int parseInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid integer for key '" + key + "': '" + value
                    + "' — using default " + defaultValue);
            return defaultValue;
        }
    }

    public int getPort() {
        return port;
    }

    public int getMaxConnection() {
        return maxConnection;
    }

    public String getCharset() {
        return charset;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getBacklog() {
        return backlog;
    }



}