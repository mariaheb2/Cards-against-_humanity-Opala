package cards_against_humanity.infrastructure.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.logging.Logger;

public class JpaConfig {

    private static final Logger LOGGER = Logger.getLogger(JpaConfig.class.getName());
    private static final String PERSISTENCE_UNIT_NAME = "cards_against_humans";
    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);

    /**
     * Força a inicialização do Hibernate na subida do servidor.
     *
     * Abre e fecha imediatamente um {@link EntityManager}, o que faz o Hibernate
     * processar {@code hbm2ddl.auto=update} e criar/atualizar todas as tabelas
     * antes de qualquer conexão de cliente chegar.
     */
    public static void initialize() {
        LOGGER.info("Initializing JPA / Hibernate schema...");
        EntityManager em = emf.createEntityManager();
        em.close();
        LOGGER.info("Database schema ready.");
    }

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