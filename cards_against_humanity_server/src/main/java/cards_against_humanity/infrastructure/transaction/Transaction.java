package cards_against_humanity.infrastructure.transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.function.Consumer;
import java.util.function.Function;

import cards_against_humanity.infrastructure.config.JpaConfig;

public class Transaction {
    public <T> T execute(Function<EntityManager, T> action) {
        EntityManager em = JpaConfig.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T result = action.apply(em);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    public void executeVoid(Consumer<EntityManager> action) {
        execute(em -> {
            action.accept(em);
            return null;
        });
    }
}