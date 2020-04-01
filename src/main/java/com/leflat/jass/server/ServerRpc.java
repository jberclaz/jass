package com.leflat.jass.server;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ServerRpc {
    private IServerNetwork network;

    ServerRpc(IServerNetwork network) {
        this.network = network;
    }

    protected String[] connectionAccepted(int playerId) throws ClientLeftException {
        network.sendStr("1 " + playerId);
        return network.rcvStr().split(" ");
    }

    public void sendPlayerInfo(Player player) throws ClientLeftException {
        network.sendStr("2 " + player.getId() + " " + player.getFirstName() + " " + player.getLastName());
        network.rcvStr();
    }

    public int chooseTeamSelectionMethod() throws ClientLeftException {
        network.sendStr("3");
        return Integer.parseInt(network.rcvStr().split(" ")[1]);
    }

    public void prepareTeamChoice() throws ClientLeftException {
        oneWayCommand(4);
    }

    public int chooseCard() throws ClientLeftException {
        network.sendStr("5");
        return Integer.parseInt(network.rcvStr().split(" ")[1]);
    }

    public void communicateCard(int playerId, int cardNumber, int card) throws ClientLeftException {
        network.sendStr("6" + playerId + " " + cardNumber + " " + card);
        network.rcvStr();
    }

    public void chooseTeamAgain() throws ClientLeftException {
        oneWayCommand(7);
    }

    public void sendPlayerOrder(Collection<Integer> ids) throws ClientLeftException {
        network.sendStr("8 " + ids.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        network.rcvStr();
    }

    public int choosePartner() throws ClientLeftException {
        network.sendStr("9");
        return Integer.parseInt(network.rcvStr().split(" ")[1]);
    }

    public void sendHand(int[] cards) throws ClientLeftException {
        var sb = new StringBuilder("10");
        for (var card : cards) {
            sb.append(" ").append(card);
        }
        network.sendStr(sb.toString());
        network.rcvStr();
    }

    public int chooseAtout() throws ClientLeftException {
        network.sendStr("11");
        return Integer.parseInt(network.rcvStr().split(" ")[0]);
    }

    public int chooseAtoutSecond() throws ClientLeftException {
        network.sendStr("12");
        return Integer.parseInt(network.rcvStr().split(" ")[0]);
    }

    public void communicateAtout(int atout) throws ClientLeftException {
        oneWayCommand(13);
    }

    public int[] playFirst() throws ClientLeftException {
        network.sendStr("14");
        return Arrays.stream(network.rcvStr().split(" ")).mapToInt(Integer::parseInt).skip(1).toArray();
    }

    public void sendPlayedCard(int playerId, Card card) throws ClientLeftException {
        network.sendStr("15 " + playerId + " " + card.getNumber());
        network.rcvStr();
    }

    public int[] playNext(int currentHighest, int currentColor, boolean cut) throws ClientLeftException {
        network.sendStr("16 " + currentHighest + " " + currentColor + " "
        + (cut? 1:0));
        return Arrays.stream(network.rcvStr().split(" ")).mapToInt(Integer::parseInt).skip(1).toArray();
    }

    public void sendPlieOwner(int playerId) throws ClientLeftException {
        network.sendStr("17 " + playerId);
        network.rcvStr();
    }

    public void sendScore(int yourScore, int theirScore) throws ClientLeftException {
        network.sendStr("18 " + yourScore + " " + theirScore);
        network.rcvStr();
    }

    public int[] requestAnoucementdetails() throws ClientLeftException {
        network.sendStr("19");
        return Arrays.stream(network.rcvStr().split(" ")).mapToInt(Integer::parseInt).skip(1).toArray();
    }

    public void sendAnouncementDetails(int playerId, List<Anouncement> anoucements) throws ClientLeftException {
        var info = new StringBuilder("20 " + playerId + " " + anoucements.size());
        for (var annoucement : anoucements) {
            info.append(" ").append(annoucement.getType()).append(" ").append(annoucement.getCard());
        }
        network.sendStr(info.toString());
        network.rcvStr();
    }

    public void sendWinner(int winningTeam, int playerId1, int playerId2) throws ClientLeftException {
        network.sendStr("21 "+ winningTeam + " " + playerId1 + " " + playerId2);
        network.rcvStr();
    }

    public boolean askNewGame() throws ClientLeftException {
        network.sendStr("22");
        return Integer.parseInt(network.rcvStr().split(" ")[1]) == 1;
    }

    public void sendPlayerLeft(int playerId) throws ClientLeftException {
        network.sendStr("23 " + playerId);
        network.rcvStr();
    }

    private void oneWayCommand(int command) throws ClientLeftException {
        network.sendStr(String.valueOf(command));
        network.rcvStr();
    }
}
