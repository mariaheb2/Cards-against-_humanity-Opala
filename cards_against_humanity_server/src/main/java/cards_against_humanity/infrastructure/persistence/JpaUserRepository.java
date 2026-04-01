package cards_against_humanity.infrastructure.persistence;

import cards_against_humanity.domain.model.User;
import cards_against_humanity.domain.repository.UserRepository;
import cards_against_humanity.infrastructure.transaction.Transaction;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Optional;

public class JpaUserRepository implements UserRepository {

    private final Transaction transaction;

    public JpaUserRepository() {
        this.transaction = new Transaction();
    }

    @Override
    public User save(User user) {
        transaction.executeVoid(em -> em.persist(user));
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        return transaction.execute(em -> Optional.ofNullable(em.find(User.class, id)));
    }

    @Override
    public List<User> findAll() {
        return transaction.execute(em ->
            em.createQuery("SELECT u FROM User u", User.class).getResultList()
        );
    }

    @Override
    public void delete(User user) {
        transaction.executeVoid(em -> {
            User managed = em.contains(user) ? user : em.merge(user);
            em.remove(managed);
        });
    }

    public void deleteById(String id) {
        transaction.executeVoid(em -> {
            User user = em.find(User.class, id);
            if (user != null) {
                em.remove(user);
            }
        });
    }   

    @Override
    public Optional<User> findByEmail(String email) {
        return transaction.execute(em -> {
            try {
                return Optional.of(em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                        .setParameter("email", email)
                        .getSingleResult());
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return transaction.execute(em -> {
            try {
                return Optional.of(em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                        .setParameter("username", username)
                        .getSingleResult());
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public boolean existsByEmail(String email) {
        return transaction.execute(em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email", Long.class)
                    .setParameter("email", email)
                    .getSingleResult();
            return count > 0;
        });
    }

    @Override
    public boolean existsByUsername(String username) {
        return transaction.execute(em -> {
            Long count = em.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult();
            return count > 0;
        });
    }
}