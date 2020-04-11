package com.leflat.jass.server;

import com.leflat.jass.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RemotePlayer extends AbstractRemotePlayer {
    private IServerNetwork network;

    public RemotePlayer(int id, IServerNetwork network) throws PlayerLeftExpection {
        super(id);
        this.network = network;
        this.network.setPlayerId(id);
        updatePlayerInfo();
    }

    @Override
    public void setPlayerInfo(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.SET_PLAYER_INFO),
                String.valueOf(player.getId()), player.getName());
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
        network.sendMessage(String.valueOf(RemoteCommand.SET_CARD),
                String.valueOf(player.getId()),
                String.valueOf(cardPosition),
                String.valueOf(card.getNumber()));
        network.receiveMessage();
    }

    @Override
    public void setPlayersOrder(List<Integer> playerIds) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_PLAYERS_ORDER));
        message.addAll(playerIds.stream().map(String::valueOf).collect(Collectors.toList()));
        network.sendMessage(message.toArray(new String[0]));
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
        network.sendMessage(message.toArray(new String[0]));
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
        network.sendMessage(String.valueOf(RemoteCommand.SET_ATOUT), String.valueOf(color), String.valueOf(firstToPlay.getId()));
        network.receiveMessage();
    }

    @Override
    public Card play() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.PLAY));
        var cardNumber = Integer.parseInt(network.receiveMessage()[0]);
        return new Card(cardNumber);
    }

    @Override
    public void setPlayedCard(BasePlayer player, Card card) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.SET_PLAYED_CARD), String.valueOf(player.getId()),String.valueOf(card.getNumber()) );
        network.receiveMessage();
    }

    @Override
    public void collectPlie(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.COLLECT_PLIE), String.valueOf(player.getId()));
        network.receiveMessage();
    }

    @Override
    public void setScores(int score, int opponentScore) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.SET_SCORES),String.valueOf(score),String.valueOf(opponentScore));
        network.receiveMessage();
    }

    @Override
    public List<Announcement> getAnnouncements() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.GET_ANOUNCEMENTS));
        var tokens = network.receiveMessage();
        this.announcements.clear();
        int nbrAnouncements = Integer.parseInt(tokens[0]);
        for (int i = 0; i < nbrAnouncements; i++) {
            var card = new Card(Integer.parseInt(tokens[i * 2 + 2]));
            this.announcements.add(new Announcement(Integer.parseInt(tokens[i * 2 + 1]), card));
        }
        return this.announcements;
    }

    @Override
    public void setAnnouncements(BasePlayer player, List<Announcement> announcements) throws PlayerLeftExpection {
        var message = new ArrayList<String>();
        message.add(String.valueOf(RemoteCommand.SET_ANOUNCEMENTS));
        message.add(String.valueOf(player.getId()));
        message.add(String.valueOf(announcements.size()));
        for (var anoucement : announcements) {
            message.add(String.valueOf(anoucement.getType()));
            message.add(String.valueOf(anoucement.getCard().getNumber()));
        }
        network.sendMessage(message.toArray(new String[0]));
        network.receiveMessage();
    }

    @Override
    public void setGameResult(Team winningTeam) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.SET_GAME_RESULT),
                String.valueOf(winningTeam.getId()),
                String.valueOf(winningTeam.getPlayer(0).getId()),
                String.valueOf(winningTeam.getPlayer(1).getId()));
        network.receiveMessage();
    }

    @Override
    public boolean getNewGame() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.GET_NEW_GAME));
        return Integer.parseInt(network.receiveMessage()[0]) == 1;
    }

    @Override
    public void playerLeft(BasePlayer player) throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(RemoteCommand.PLAYER_LEFT),
                String.valueOf(player.getId()));
        network.receiveMessage();
    }

    @Override
    public void lostServerConnection() {

    }

    private void updatePlayerInfo() throws PlayerLeftExpection {
        network.sendMessage(String.valueOf(id));
        this.name = network.receiveMessage()[0];
    }
}
