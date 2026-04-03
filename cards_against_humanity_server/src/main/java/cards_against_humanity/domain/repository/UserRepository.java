package cards_against_humanity.domain.repository;

import java.util.Optional;

import cards_against_humanity.domain.model.User;

public interface UserRepository extends Repository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
}