package cards_against_humanity.infrastructure.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.logging.Logger;

public class JpaConfig {

    private static final Logger LOGGER = Logger.getLogger(JpaConfig.class.getName());
    private static final String PERSISTENCE_UNIT_NAME = "cardsPU";
    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);

    public static EntityManager createEntityManager() {
        return emf.createEntityManager();
    }

    public static void close() {
        if (emf.isOpen()) {
            emf.close();
            LOGGER.info("EntityManagerFactory closed.");
        }
    }
}