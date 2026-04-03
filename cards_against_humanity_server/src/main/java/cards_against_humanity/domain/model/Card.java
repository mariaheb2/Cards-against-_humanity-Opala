package cards_against_humanity.domain.model;

import cards_against_humanity.domain.model.enums.CardType;
import jakarta.persistence.*;

@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @Column(nullable = false, length = 500)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType type;

    protected Card() {
        // JPA
    }

    public Card(String text, CardType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public CardType getType() {
        return type;
    }

    public void setText(String text) {
        this.text = text;
    }
}