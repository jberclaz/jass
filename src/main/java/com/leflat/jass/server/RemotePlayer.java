package com.leflat.jass.server;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.PlayerLeftExpection;
import com.leflat.jass.common.TeamSelectionMethod;

import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.joining;

public class RemotePlayer extends BasePlayer {
    private Rpc network;

    public RemotePlayer(int id, Rpc network) throws PlayerLeftExpection {
        super(id);
        this.network = network;
        updatePlayerInfo();
    }

    @Override
    public void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage("2 " + player.getId() + " " + player.getName());
        network.receiveMessage();
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() throws PlayerLeftExpection {
        network.sendMessage("3");
        var tokens = network.receiveMessage();
        return TeamSelectionMethod.valueOf(tokens[0]);
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) throws PlayerLeftExpection {
        if (firstAttempt) {
            network.sendMessage("4");
        } else {
            network.sendMessage("7");
        }
        network.receiveMessage();
    }

    @Override
    public int drawCard() throws PlayerLeftExpection {
        network.sendMessage("5");
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) throws PlayerLeftExpection {
        network.sendMessage("6 " + player.getId() + " " + cardPosition + " " + card.getNumber());
        network.receiveMessage();
    }

    @Override
    public void setPlayerOrder(List<BasePlayer> players) throws PlayerLeftExpection {
        network.sendMessage("8 " + players.stream().map(p -> String.valueOf(p.getId())).collect(joining(" ")));
        network.receiveMessage();
    }

    @Override
    public int choosePartner() throws PlayerLeftExpection {
        network.sendMessage("9");
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        var sb = new StringBuilder("10");
        for (var card : cards) {
            sb.append(" ").append(card.getNumber());
        }
        network.sendMessage(sb.toString());
        network.receiveMessage();
    }

    @Override
    public int chooseAtout(boolean first) throws PlayerLeftExpection {
        if (first) {
            network.sendMessage("11");
        } else {
            network.sendMessage("12");
        }
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {
        network.sendMessage("13 " + color + " " + firstToPlay.getId());
        network.receiveMessage();
    }

    @Override
    public void play(int currentColor, int highestRank, boolean cut) throws PlayerLeftExpection {
        if (currentColor < 0) {
            network.sendMessage("14");
        } else {
            network.sendMessage("16 " + highestRank + " " + currentColor + " " + (cut ? 1 : 0));
        }
        network.receiveMessage();
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {
        network.sendMessage("15 " + player.getId() + " " + card.getNumber());
        network.receiveMessage();
    }

    @Override
    public void setPlieOwner(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage("17 " + player.getId());
        network.receiveMessage();
    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {
        network.sendMessage("18 " + score + " " + opponentScore);
        network.receiveMessage();
    }

    @Override
    public List<Anouncement> getAnoucement() throws PlayerLeftExpection {
        network.sendMessage("19");
        var tokens = network.receiveMessage();
        List<Anouncement> anouncements = new ArrayList<>();
        int nbrAnouncements = Integer.parseInt(tokens[0]);
        for (int i = 0; i < nbrAnouncements; i++) {
            var card = new Card(Integer.parseInt(tokens[i * 2 + 2]));
            anouncements.add(new Anouncement(Integer.parseInt(tokens[i * 2 + 1]), card));
        }
        return anouncements;
    }

    @Override
    public void setAnouncement(BasePlayer player, List<Anouncement> anouncements) throws PlayerLeftExpection {
        var info = new StringBuilder("20 " + player.getId() + " " + anouncements.size());
        for (var anoucement : anouncements) {
            info.append(" ").append(anoucement.getType()).append(" ").append(anoucement.getCard());
        }
        network.sendMessage(info.toString());
        network.receiveMessage();
    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {
        network.sendMessage("21 " + winningTeam.getId() + " " + winningTeam.getPlayer(0) + " " + winningTeam.getPlayer(1));
        network.receiveMessage();
    }

    @Override
    public boolean getNewGame() throws PlayerLeftExpection {
        network.sendMessage("22");
        return Integer.parseInt(network.receiveMessage()[0]) == 1;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage("23 " + player.getId());
        network.receiveMessage();
    }

    private void updatePlayerInfo() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(id));
        this.name = network.receiveMessage()[0];
    }
}
