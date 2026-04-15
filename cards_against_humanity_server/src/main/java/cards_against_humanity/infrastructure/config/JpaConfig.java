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
                dbHost, dbPort, dbName);

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
                        "Meu plano de aposentadoria: ___",
                        "O segredo do meu sucesso é ___",
                        "Minha mãe nunca vai me perdoar por ___",
                        "Nada arruina mais um jantar em família do que ___",
                        "Se eu fosse presidente, meu primeiro decreto seria ___",
                        "Meu histórico de busca não pode revelar ___",
                        "O verdadeiro significado da vida é ___",
                        "O que eu levo comigo para uma ilha deserta: ___",
                        "O maior erro da humanidade foi ___",
                        "O que aconteceu naquela festa foi culpa de ___",
                        "O próximo reality show será sobre ___"
                };
                for (String q : questions) {
                    em.persist(new cards_against_humanity.domain.model.Card(q,
                            cards_against_humanity.domain.model.enums.CardType.QUESTION));
                }

                String[] answers = {
                        "Um grupo de coach quântico gritando comigo",
                        "Fingir que entendi a piada",
                        "Um PowerPoint de 200 slides às 7 da manhã",
                        "Aquele silêncio constrangedor no elevador",
                        "Um esquema de pirâmide claramente suspeito",
                        "Meu chefe dizendo 'rapidinho'",
                        "Um tutorial de 3 horas que não resolve nada",
                        "Um grupo de WhatsApp da família às 6h",
                        "A vontade de sumir e virar ermitão",
                        "Comer como se ninguém estivesse olhando (mas estão)",
                        "Um hack de produtividade que só piora tudo",
                        "A falsa sensação de que sei o que estou fazendo",
                        "Um bug que só aparece em produção",
                        "Trabalhar 8h e ainda fingir que gosto",
                        "Aquele print comprometedor",
                        "Um plano brilhante que deu muito errado",
                        "Dormir e ignorar responsabilidades",
                        "Uma risada em momento totalmente inapropriado",
                        "Uma desculpa tão ruim que ninguém acredita",
                        "O estagiário fazendo melhor que todo mundo",
                        "Uma reunião que poderia ser um e-mail",
                        "Prometer começar a vida fitness na segunda",
                        "Um talento inútil que ninguém pediu",
                        "A arte de procrastinar profissionalmente",
                        "Um olhar julgador do nada",
                        "Tomar decisões baseadas em meme",
                        "Um passado que me persegue às 3 da manhã",
                        "Confiança demais e habilidade de menos",
                        "Um tutorial feito por alguém que odeia iniciantes",
                        "A coragem de falar besteira com convicção",
                        "Um complô do governo"
                };
                for (String a : answers) {
                    em.persist(new cards_against_humanity.domain.model.Card(a,
                            cards_against_humanity.domain.model.enums.CardType.ANSWER));
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