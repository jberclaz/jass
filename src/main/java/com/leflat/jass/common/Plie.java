package com.leflat.jass.common;


import java.util.ArrayList;
import java.util.List;

public class Plie {
    private Card highest;          // la plus haute carte de la plie (celle qui tient la plie)
    private boolean cut = false;
    private BasePlayer owner;      // celui qui tient la plie
    private ArrayList<Card> cards = new ArrayList<>();

    public Plie() {
    }

    public Plie(Card card, BasePlayer player) {
        cards.add(card);
        highest = card;
        this.owner = player;
    }

    public int getColor() {
        return cards.get(0).getColor();
    }

    public int getHighest() {
        return highest.getRank();
    }

    public int getScore() {
        int score = cards.stream().mapToInt(Card::getValue).sum();
        return Card.atout == Card.COLOR_SPADE ? 2 * score : score;
    }

    public BasePlayer getOwner() {
        return owner;
    }

    public boolean isCut() {
        return cut;
    }

    public int getSize() {
        return cards.size();
    }

    public void playCard(Card card, BasePlayer player, List<Card> hand) throws BrokenRuleException {
        if (cards.isEmpty()) {
            takePlie(card, player);
        } else if (card.getColor() == this.getColor()) {
            follow(card, player);
        } else {
            doesNotFollow(card, player, hand);
        }
    }

    private void follow(Card card, BasePlayer player) {
        if (!cut && card.compareTo(highest) > 0) {
            takePlie(card, player);
            return;
        }
        cards.add(card);
    }

    private void doesNotFollow(Card card, BasePlayer player, List<Card> hand) throws BrokenRuleException {
        if (card.getColor() == Card.atout) {
            cutPlie(card, player, hand);
            return;
        }
        boolean hasAskedColor = hand.stream().anyMatch(c -> c.getColor() == this.getColor());
        if (hasAskedColor) {
            if (!Rules.hasBourgSec(hand)) {
                throw new BrokenRuleException(Rules.RULES_MUST_FOLLOW);
            }
        }
        cards.add(card);
    }

    private void cutPlie(Card card, BasePlayer player, List<Card> hand) throws BrokenRuleException {
        if (!cut) {
            takePlie(card, player);
            cut = true;
            return;
        }
        if (card.compareTo(highest) > 0) {
            takePlie(card, player);
            return;
        }
        boolean hasNonAtoutCards = hand.stream().anyMatch(c -> c.getColor() != Card.atout);
        if (hasNonAtoutCards) {
            throw new BrokenRuleException(Rules.RULES_CANNOT_UNDERCUT);
        }
        boolean hasHigherAtoutCards = hand.stream().anyMatch(c->c.compareTo(highest) > 0);
        if (hasHigherAtoutCards ) {
            throw new BrokenRuleException(Rules.RULES_CANNOT_UNDERCUT);
        }
        cards.add(card);
    }

    private void takePlie(Card card, BasePlayer player) {
        highest = card;
        owner = player;
        cards.add(card);
    }
}
