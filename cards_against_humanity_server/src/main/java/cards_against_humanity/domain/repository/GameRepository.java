package cards_against_humanity.domain.repository;

import java.util.List;
import java.util.Optional;

import cards_against_humanity.domain.model.Game;
import cards_against_humanity.domain.model.enums.GameState;

public interface GameRepository extends Repository<Game, String> {

    List<Game> findByState(GameState state);

    Optional<Game> findByPlayerId(String playerId);
}