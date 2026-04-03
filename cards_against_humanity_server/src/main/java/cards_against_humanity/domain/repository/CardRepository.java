package cards_against_humanity.domain.repository;

import java.util.List;

import cards_against_humanity.domain.model.Card;
import cards_against_humanity.domain.model.enums.CardType;

public interface CardRepository extends Repository<Card, String> {

    List<Card> findByType(CardType type);

    List<Card> findRandomAnswers(int limit);

    List<Card> findRandomQuestions(int limit);
}