package cards_against_humanity;

import cards_against_humanity.infrastructure.config.JpaConfig;
import cards_against_humanity.server.ServerConfig;
import cards_against_humanity.server.TcpServer;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Application entry point.
 * Boots the TCP server using settings from
 * {@code src/main/resources/config/config.properties} and registers a JVM
 * shutdown hook so the server stops gracefully on Ctrl+C or SIGTERM.
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        ServerConfig config = new ServerConfig();
        TcpServer server = new TcpServer(config);

        // Shutdown on Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown signal received — stopping server...");
            server.stop();
            JpaConfig.close();
        }, "shutdown-hook"));

        try {
            // Garante que o banco e todas as tabelas existam antes de aceitar conexões
            JpaConfig.initialize();

            server.start();
            LOGGER.info("Server is running on port " + config.getPort() + ". Press Ctrl+C to stop.");

            // Keep the main thread alive while the accept loop runs in its daemon thread.
            Thread.currentThread().join();

        } catch (IOException e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
