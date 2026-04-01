package cards_against_humanity.domain.repository;

import java.util.List;
import java.util.Optional;

import cards_against_humanity.domain.model.Player;

public interface PlayerRepository extends Repository<Player, String> {

    List<Player> findByGameId(String gameId);

    Optional<Player> findByUserIdAndGameId(String userId, String gameId);
}