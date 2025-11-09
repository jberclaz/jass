package com.leflat.jass.server;

import com.leflat.jass.common.Card;
import com.leflat.jass.common.PlayerPosition;
import com.leflat.jass.common.Plie;

import java.util.*;
import java.util.logging.Logger;

public class GameView {
    protected final static Logger LOGGER = Logger.getLogger(GameView.class.getName());
    private final Random rand = new Random();
    private final Map<Integer, Float[]> unknownCardsInGame = new HashMap<>();
    private final List<Card>[] knownCardsInHands = new List[3];
    private final int[] handSizes = new int[3];
    private List<Card> ownHand;
    private PlayerPosition trumpChoser;
    private boolean wasTrumpChosenOnFirstTurn;
    private int ourGameScore;
    private int opponentGameScore;
    private int ourMatchScore;
    private int opponentMatchScore;
    private final List<Card> currentTrick = new ArrayList<>();
    private PlayerPosition firstToPlayTrick;
    private final List<Plie> lastCompletedTricks = new ArrayList<>();
    private final Map<Integer, PlayerPosition> positionsByIds = new HashMap<>();

    public GameView() {
        for (int p = 0; p < 3; p++) {
            knownCardsInHands[p] = new ArrayList<>();
        }
    }

    public void reset(List<Card> ownHand, Map<Integer, PlayerPosition> positionsByIds) {
        unknownCardsInGame.clear();
        for (int p = 0; p < 3; p++) {
            knownCardsInHands[p].clear();
            handSizes[p] = 9;
        }
        for (int i = 0; i < Card.DECK_SIZE; i++) {
            var card = new Card(i);
            if (ownHand.contains((card))) {
                continue;
            }
            unknownCardsInGame.put(i, new Float[]{1 / 3f, 1 / 3f, 1 / 3f});
        }
        this.ownHand = new ArrayList<>(ownHand);
        assert getNumberCardsInGame() == 27;
        this.positionsByIds.putAll(positionsByIds);
        ourGameScore = 0;
        opponentGameScore = 0;
        lastCompletedTricks.clear();
    }

    public void cardPlayed(PlayerPosition position, Card card) {
        if (position == PlayerPosition.SELF) {
            ownHand.remove(card);
            return;
        }
        int positionIndex = position.getCode() - 1;
        int previousNumberCardsInGame = getNumberCardsInGame();
        var removedCardFromGame = unknownCardsInGame.remove(card.getNumber());
        var removedCardFromHand = knownCardsInHands[positionIndex].remove(card);
        if ((removedCardFromGame != null) == removedCardFromHand) {
            throw new RuntimeException("Card " + card + " played by player " + position + " was not properly accounted for in GameView");
        }
        handSizes[positionIndex] --;
        assert getNumberCardsInGame() == (previousNumberCardsInGame - 1);
        if (currentTrick.isEmpty()) {
            firstToPlayTrick = position;
        }
        currentTrick.add(card);
    }

    public void playerHasCard(PlayerPosition position, int cardNumber) {
        playerHasCard(position, new Card(cardNumber));
    }

    public void playerHasCard(PlayerPosition position, Card card) {
        assert position != PlayerPosition.SELF;
        int positionIndex = position.getCode() - 1;
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (!unknownCardsInGame.containsKey(card.getNumber())) {
            return;
        }
        if (knownCardsInHands[positionIndex].contains(card)) {
            throw new RuntimeException("We already know player " + position + " has card " + card);
        }
        unknownCardsInGame.remove(card.getNumber());
        knownCardsInHands[positionIndex].add(card);
        assert getNumberCardsInGame() == previousNumberCardsInGame : getNumberCardsInGame() + " != " + previousNumberCardsInGame;
    }

    public void playerDoesNotHaveCard(PlayerPosition position, int cardNumber) {
        assert position != PlayerPosition.SELF;
        int positionIndex = position.getCode() - 1;
        int previousNumberCardsInGame = getNumberCardsInGame();
        if (knownCardsInHands[positionIndex].contains(new Card(cardNumber))) {
            throw new RuntimeException("Contradictory game view: strategy believes that player " + position + " has card " + cardNumber);
        }
        var prob = unknownCardsInGame.get(cardNumber);
        if (prob == null) {
            return;
        }
        if (prob[positionIndex] > 0) {
            prob[positionIndex] = 0f;
            normalizeCardsProbabilities(cardNumber);
        }
        assert getNumberCardsInGame() == previousNumberCardsInGame;
    }

    public void playerDoesNotHaveCard(PlayerPosition position, Card card) {
        playerDoesNotHaveCard(position, card.getNumber());
    }

    public void setTrump(PlayerPosition position, boolean chosenOnFirstTurn) {
        trumpChoser = position;
        wasTrumpChosenOnFirstTurn = chosenOnFirstTurn;
    }

    public void setCompletedTrick(Plie lastTrick) {
        lastCompletedTricks.add(lastTrick);
        var ownerPosition = positionsByIds.get(lastTrick.getOwner().getId());
        if (ownerPosition.ourTeam()) {
            ourGameScore += lastTrick.getScore();
        } else {
            opponentGameScore += lastTrick.getScore();
        }
        currentTrick.clear();
    }

    public void addAnnouncementScore(int score, boolean ourTeam) {
        if (ourTeam) {
            ourGameScore += score;
        } else {
            opponentGameScore += score;
        }
    }

    private void normalizeCardsProbabilities(int cardNumber) {
        var prob = unknownCardsInGame.get(cardNumber);
        float sum = prob[0] + prob[1] + prob[2];
        if (sum == 0) {
            throw new RuntimeException("Probabilities should not sum to zero");
        }
        int uniqueIndex = -1;
        for (int p = 0; p < 3; p++) {
            if (prob[p] == sum) {
                uniqueIndex = p;
                break;
            }
            prob[p] /= sum;
        }
        if (uniqueIndex >= 0) {
            playerHasCard(PlayerPosition.fromCode(uniqueIndex + 1), cardNumber);
        }
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        for (var card : unknownCardsInGame.entrySet()) {
            var prob = card.getValue();
            s.append(new Card(card.getKey()).toString()).append(" : ").append(prob[0]).append(", ").append(prob[1]).append(", ").append(prob[2]).append("\n");
        }
        for (int p = 0; p < 3; p++) {
            if (!knownCardsInHands[p].isEmpty()) {
                s.append(p).append(": ");
                for (var card : knownCardsInHands[p]) {
                    s.append(card).append(" ");
                }
                s.append("\n");
            }
        }
        return s.toString();
    }

    public List<Card>[] getRandomHands() {
        assert getNumberCardsInGame() == (handSizes[0] + handSizes[1] + handSizes[2]);
        List<Card>[] hands = new List[3];
        for (int p = 0; p < 3; p++) {
            hands[p] = new ArrayList<>(knownCardsInHands[p]);
        }
        int[] cardsSum = new int[3];
        for (var prob : unknownCardsInGame.values()) {
            for (int p = 0; p < 3; p++) {
                if (prob[p] > 0) {
                    cardsSum[p]++;
                }
            }
        }

        var sortedEntrySet = new ArrayList<>(unknownCardsInGame.entrySet());
        sortedEntrySet.sort((e1, e2) -> {
            var p1 = e1.getValue();
            var p2 = e2.getValue();
            int zeros1 = 0, zeros2 = 0;
            for (int p = 0; p < 3; p++) {
                if (p1[p] == 0) {
                    zeros1++;
                }
                if (p2[p] == 0) {
                    zeros2++;
                }
            }
            return zeros2 - zeros1;
        });
        for (var card : sortedEntrySet) {
            var probs = card.getValue();
            boolean attributed = false;
            for (int p = 0; p < 3; p++) {
                if (cardsSum[p] == handSizes[p] - hands[p].size()) {
                    if (probs[p] > 0) {
                        hands[p].add(new Card(card.getKey()));
                        attributed = true;
                        break;
                    }
                }
            }
            for (int p = 0; p < 3; p++) {
                if (probs[p] > 0) {
                    cardsSum[p]--;
                }
            }
            if (attributed) {
                continue;
            }
            int player = -1;
            do {
                float sumProb = 0;
                float f = rand.nextFloat();
                for (int p = 0; p < 3; p++) {
                    sumProb += probs[p];
                    if (f < sumProb) {
                        player = p;
                        break;
                    }
                }
            } while (hands[player].size() == handSizes[player]);
            hands[player].add(new Card(card.getKey()));
        }
        return hands;
    }

    int getNumberCardsInGame() {
        return unknownCardsInGame.size() + knownCardsInHands[0].size() + knownCardsInHands[1].size() + knownCardsInHands[2].size();
    }

    public void updateMatchScore(int ourScore, int opponentScore) {
        ourMatchScore = ourScore;
        opponentMatchScore = opponentScore;
    }

    record CardProbability(int cardId, float probability) {
    }

    List<CardProbability> getKMostProbableCards(int playerIndex, int k, float threshold) {
        return unknownCardsInGame.entrySet().stream()
                // 1. Map each entry to a CardProbability record for the target player
                .map(entry -> {
                    int cardId = entry.getKey();
                    Float[] probs = entry.getValue();
                    return new CardProbability(cardId, probs[playerIndex]);
                })
                // 2. Filter out any cards this player has a 0% chance of holding
                .filter(cp -> cp.probability() > threshold)
                // 3. Sort by probability, descending.
                // We use .reversed() to flip the natural ascending order.
                .sorted(Comparator.comparingDouble(CardProbability::probability).reversed())
                // 4. Limit the results to the top k
                .limit(k)
                // 5. Collect into an immutable List (requires Java 16+)
                .toList();
    }

    public byte[] encodeStateForTransformer() {
        List<Integer> tokens = new ArrayList<>();

        // === 0: CLS ===
        tokens.add(Tokens.CLS);

        // === 1-9: GLOBALS (9 tokens) ===
        tokens.add(Tokens.SECTION_GLOBALS);

        // Trump signal triplet: [TYPE] [PLAYER] [COLOR]
        tokens.add(wasTrumpChosenOnFirstTurn ? Tokens.TRUMP_FIRST_CHOICE : Tokens.TRUMP_FORCED);  // 2
        tokens.add(Tokens.positionToken(trumpChoser));                                       // 3
        tokens.add(Tokens.trumpToken(Card.atout));                            // 4

        // Scores — pure positional
        tokens.add(Tokens.scoreToken(ourGameScore));                              // 5
        tokens.add(Tokens.scoreToken(opponentGameScore));                            // 6
        tokens.add(Tokens.globalScoreToken(ourMatchScore));                   // 7
        tokens.add(Tokens.globalScoreToken(opponentMatchScore));                 // 8

        assert tokens.size() == 9;
        // === 9-18: HAND (9 cards + pad) ===
        tokens.add(Tokens.SECTION_HAND);
        List<Card> sorted = new ArrayList<>(ownHand);
        sorted.sort(Comparator.comparingInt(Card::getNumber));
        for (int i = 0; i < 9; i++) {
            tokens.add(i < sorted.size() ? Tokens.cardToken(sorted.get(i)) : Tokens.PAD);
        }
        assert tokens.size() == 19;

        // === 19-24: CURRENT TRICK (≤3 cards → 6 tokens) ===
        tokens.add(Tokens.SECTION_TRICK);
        int played = currentTrick.size();
        for (int i = 0; i < played; i++) {
            Card c = currentTrick.get(i);
            PlayerPosition pos = PlayerPosition.fromCode((firstToPlayTrick.getCode() + i) % 4);
            tokens.add(Tokens.positionToken(pos));
            tokens.add(Tokens.cardToken(c));
        }
        while (tokens.size() < 26) tokens.add(Tokens.PAD);  // pad to 6

        assert tokens.size() == 26;

        // === 25-64: HISTORY (5 tricks × 5 tokens = 40) ===
        tokens.add(Tokens.SECTION_HISTORY);
        for (Plie trick : lastCompletedTricks) {
                for (var c : trick.getCards()) {
                    tokens.add(c != null ? Tokens.cardToken(c) : Tokens.PAD);
                }
                boolean weWon = positionsByIds.get(trick.getOwner().getId()).ourTeam();
                tokens.add(weWon ? Tokens.WIN_OUR_TEAM : Tokens.WIN_OPP_TEAM);
        }
        for (int i=0; i<(8 - lastCompletedTricks.size()); i++) {
            for (int j = 0; j < 5; j++) tokens.add(Tokens.PAD);
        }

        assert tokens.size() == 67;

        // === 65-94: BELIEF (10 × 3 = 30 tokens) ===
        tokens.add(Tokens.SECTION_BELIEF);
        final int maxCardsPerPlayer = 4;
        for (int pos : new int[]{1, 2, 3}) {
            tokens.add(Tokens.positionToken(PlayerPosition.fromCode(pos)));
            int cardsCount = 0;
            for (int i=0; i<Math.min(maxCardsPerPlayer, knownCardsInHands[pos-1].size()); i++) {
                tokens.add(Tokens.cardToken(knownCardsInHands[pos-1].get(i)));
                tokens.add(Tokens.confidenceToken(1));
                cardsCount++;
            }
            if (cardsCount < maxCardsPerPlayer) {
                for (var items : getKMostProbableCards(pos - 1, maxCardsPerPlayer - cardsCount, 0.34f)) {
                    tokens.add(Tokens.cardToken(items.cardId));
                    tokens.add(Tokens.confidenceToken(items.probability));
                    cardsCount++;
                }
            }
            for (int i=cardsCount; i<maxCardsPerPlayer; i++) {
                tokens.add(Tokens.PAD);
                tokens.add(Tokens.PAD);
            }
        }
        assert tokens.size() == 95;
        byte[] result = new byte[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            result[i] = (byte) (tokens.get(i) & 0xFF);
        }
        return result;
    }


}
