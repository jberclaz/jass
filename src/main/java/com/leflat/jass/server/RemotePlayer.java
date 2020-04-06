package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RemotePlayer extends BasePlayer implements IPlayer {
    private INetwork network;

    public RemotePlayer(int id, INetwork network) throws PlayerLeftExpection {
        super(id);
        this.network = network;
        this.network.setPlayerId(id);
        updatePlayerInfo();
    }

    @Override
    public void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_PLAYER_INFO));
        message.add(String.valueOf(player.getId()));
        message.add(player.getName());
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public TeamSelectionMethod chooseTeamSelectionMethod() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.CHOOSE_TEAM_SELECTION_METHOD));
        var tokens = network.receiveMessage();
        return TeamSelectionMethod.valueOf(tokens[0]);
    }

    @Override
    public void prepareTeamDrawing(boolean firstAttempt) throws PlayerLeftExpection {
        if (firstAttempt) {
            network.sendMessage(String.valueOf(RemoteCommand.PREPARE_TEAM_DRAWING));
        } else {
            network.sendMessage(String.valueOf(RemoteCommand.RESTART_TEAM_DRAWING));
        }
        network.receiveMessage();
    }

    @Override
    public int drawCard() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.DRAW_CARD));
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setCard(BasePlayer player, int cardPosition, Card card) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_CARD));
        message.add(String.valueOf(player.getId()));
        message.add(String.valueOf(cardPosition));
        message.add(String.valueOf(card.getNumber()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_PLAYERS_ORDER));
        message.addAll(playerIds.stream().map(String::valueOf).collect(Collectors.toList()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public int choosePartner() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.CHOOSE_PARTNER));
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setHand(List<Card> cards) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_HAND));
        message.addAll(cards.stream().map(c -> String.valueOf(c.getNumber())).collect(Collectors.toList()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public int chooseAtout(boolean first) throws PlayerLeftExpection {
        if (first) {
            network.sendMessage(String.valueOf(RemoteCommand.CHOOSE_ATOUT));
        } else {
            network.sendMessage(String.valueOf(RemoteCommand.CHOOSE_ATOUT_SECOND));
        }
        return Integer.parseInt(network.receiveMessage()[0]);
    }

    @Override
    public void setAtout(int color, BasePlayer firstToPlay) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_ATOUT));
        message.add(String.valueOf(color));
        message.add(String.valueOf(firstToPlay.getId()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public Card play(int currentColor, int highestRank, boolean cut) throws PlayerLeftExpection {
        if (currentColor < 0) {
            network.sendMessage(String.valueOf(RemoteCommand.PLAY));
        } else {
            var message = new ArrayList<String>();
            message.add(String.valueOf(RemoteCommand.PLAY_NEXT));
            message.add(String.valueOf(highestRank));
            message.add(String.valueOf(currentColor));
            message.add(String.valueOf(cut ? 1 : 0));
            network.sendMessage(message);
        }
        var cardNumber = Integer.parseInt(network.receiveMessage()[0]);
        return new Card(cardNumber);
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_PLAYED_CARD));
        message.add(String.valueOf(player.getId()));
        message.add(String.valueOf(card.getNumber()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public void setPlieOwner(BasePlayer player) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_PLIE_OWNER));
        message.add(String.valueOf(player.getId()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_SCORES));
        message.add(String.valueOf(score));
        message.add(String.valueOf(opponentScore));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public List<Anouncement> getAnoucement() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.GET_ANOUNCEMENTS));
        var tokens = network.receiveMessage();
        this.anoucements.clear();
        int nbrAnouncements = Integer.parseInt(tokens[0]);
        for (int i = 0; i < nbrAnouncements; i++) {
            var card = new Card(Integer.parseInt(tokens[i * 2 + 2]));
            this.anoucements.add(new Anouncement(Integer.parseInt(tokens[i * 2 + 1]), card));
        }
        return this.anoucements;
    }

    @Override
    public void setAnouncement(BasePlayer player, List<Anouncement> anouncements) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_ANOUNCEMENTS));
        message.add(String.valueOf(player.getId()));
        message.add(String.valueOf(anouncements.size()));
        for (var anoucement : anouncements) {
            message.add(String.valueOf(anoucement.getType()));
            message.add(String.valueOf(anoucement.getCard().getNumber()));
        }
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_GAME_RESULT));
        message.add(String.valueOf(winningTeam.getId()));
        message.add(String.valueOf(winningTeam.getPlayer(0)));
        message.add(String.valueOf(winningTeam.getPlayer(1)));
        network.sendMessage(message);
        network.receiveMessage();
    }

    @Override
    public boolean getNewGame() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.GET_NEW_GAME));
        return Integer.parseInt(network.receiveMessage()[0]) == 1;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.PLAYER_LEFT));
        message.add(String.valueOf(player.getId()));
        network.sendMessage(message);
        network.receiveMessage();
    }

    private void updatePlayerInfo() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(id));
        this.name = network.receiveMessage()[0];
    }
}
