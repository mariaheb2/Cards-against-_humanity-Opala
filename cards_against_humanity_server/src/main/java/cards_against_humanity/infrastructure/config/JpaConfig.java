package cards_against_humanity.infrastructure.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class JpaConfig {

    private static final Logger LOGGER = Logger.getLogger(JpaConfig.class.getName());
    private static final String PERSISTENCE_UNIT_NAME = "cards_against_humans";
    private static final EntityManagerFactory emf = createEntityManagerFactory();

    private static EntityManagerFactory createEntityManagerFactory() {
        // Valores padrão
        String dbHost = getEnv("DB_HOST", "localhost");
        String dbPort = getEnv("DB_PORT", "3306");
        String dbName = getEnv("DB_NAME", "cards_db");
        String dbUser = getEnv("DB_USER", "game");
        String dbPassword = getEnv("DB_PASSWORD", "123");

        String jdbcUrl = String.format(
            "jdbc:mysql://%s:%s/%s?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            dbHost, dbPort, dbName
        );

        LOGGER.info("Connecting to database: " + jdbcUrl + " with user " + dbUser);

        Map<String, String> props = new HashMap<>();
        props.put("jakarta.persistence.jdbc.url", jdbcUrl);
        props.put("jakarta.persistence.jdbc.user", dbUser);
        props.put("jakarta.persistence.jdbc.password", dbPassword);

        return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, props);
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    public static void initialize() {
        LOGGER.info("Initializing JPA / Hibernate schema...");
        EntityManager em = emf.createEntityManager();
        
        try {
            Long cardCount = em.createQuery("SELECT COUNT(c) FROM Card c", Long.class).getSingleResult();
            if (cardCount == 0) {
                LOGGER.info("Database is empty. Populating with initial cards...");
                em.getTransaction().begin();
                
                String[] questions = {
                    "Por que cheguei atrasado: ___",
                    "___ é a razão pela qual choro no chuveiro.",
                    "O que me mantém acordado à noite: ___",
                    "Segundo os cientistas, ___ é a causa do aquecimento global.",
                    "Meu plano de aposentadoria: ___"
                };
                for (String q : questions) {
                    em.persist(new cards_against_humanity.domain.model.Card(q, cards_against_humanity.domain.model.enums.CardType.QUESTION));
                }
                
                String[] answers = {
                    "Uma batata quente",
                    "Morgan Freeman narrando minha vida",
                    "O Wi-Fi caindo na hora errada",
                    "Aquela sensação de déjà vu",
                    "Um cachorro usando óculos",
                    "Acusar o estagiário",
                    "Ovos mexidos às 3 da manhã",
                    "Um complô do governo",
                    "Cheiro de gasolina",
                    "Nicolas Cage em seu melhor momento"
                };
                for (String a : answers) {
                    em.persist(new cards_against_humanity.domain.model.Card(a, cards_against_humanity.domain.model.enums.CardType.ANSWER));
                }
                
                em.getTransaction().commit();
                LOGGER.info("Initial cards injected successfully!");
            }
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            LOGGER.severe("Failed to inject initial cards: " + e.getMessage());
        } finally {
            em.close();
        }
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