package cards_against_humanity.domain.repository;

import java.util.List;
import java.util.Optional;

import cards_against_humanity.domain.model.PlayedCard;

public interface PlayedCardRepository extends Repository<PlayedCard, String> {

    List<PlayedCard> findByGameId(String gameId);

    Optional<PlayedCard> findWinnerByGameId(String gameId);
}