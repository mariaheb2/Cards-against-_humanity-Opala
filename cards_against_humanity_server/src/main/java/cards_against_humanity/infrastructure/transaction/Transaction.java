package cards_against_humanity.infrastructure.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.function.Consumer;
import java.util.function.Function;

import cards_against_humanity.infrastructure.config.JpaConfig;

public class Transaction {

    private final EntityManager entityManager;

    public Transaction() {
        this.entityManager = JpaConfig.createEntityManager();
    }

    public <T> T execute(Function<EntityManager, T> action) {
        EntityTransaction tx = entityManager.getTransaction();
        try {
            tx.begin();
            T result = action.apply(entityManager);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            entityManager.close();
        }
    }

    public void executeVoid(Consumer<EntityManager> action) {
        execute(em -> {
            action.accept(em);
            return null;
        });
    }
}