/*
 * FlatJassClientSystem.java
 *
 * Created on 18. avril 2000, 16:09
 */

/**
 * @author Berclaz Jérôme
 * @version 1.2
 */

package com.leflat.jass.client;

import com.leflat.jass.common.Anouncement;
import com.leflat.jass.common.Card;
import com.leflat.jass.common.Plie;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlatJassClientSystem {
    // Variables
    private ClientFrame frame = new ClientFrame(this);

    // Variables du moteur de jeu
    private ClientListener listener;                       // classe qui écoute
    private ClientNetwork network = new ClientNetwork();   // classe qui implémente socket
    private int playerId;                                  // id du joueur
    private ClientPlayer[] players = new ClientPlayer[4];  // les 4 joueurs
    private ArrayList<Card> hand = new ArrayList<>();      // main du joueur
    private int atout;                                     // atout
    private Plie currentPlie = new Plie();                 // plie en cours
    private ArrayList<Anouncement> myAnouncement = new ArrayList<>(); // annonces
    private int stoeck;                                    // 0:no stoeck, 1:got stöck, 2: joué un, 3:joué les deux
    private int plieNbr;                                   // numéro de la plie

    /**
     * Creates new FlatJassClientSystem
     */
    public FlatJassClientSystem() {
        for (int i = 0; i < 4; i++)             // construire les objets player
            players[i] = new ClientPlayer();
        frame.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        new FlatJassClientSystem();
    }

    public int connect(String firstName, String lastName, String IP) {
        // renvoie -1 en cas d'échec et 0 si réussi
        System.out.println("Connected to " + IP);
        Socket cs = network.connect(IP);
        if (cs != null) {         // connexion réussie
            players[0].setFirstName(firstName);
            players[0].setLastName(lastName);
            if (listener == null) {
                listener = new ClientListener(this, cs);
                System.out.println("Création d'un nouveau listener"); // débuggage
            } else {
                // listener.setSocket(cs);
                // listener.stop = false;
                listener = new ClientListener(this, cs);
            }
            listener.start();
        } else {
            return -1;
        }

        //players[0].setFirstName(firstName);
        //players[0].setLastName(lastName);
        // listener = new ClientListener(this);
        // listener.start();
        return 0;
    }

    // Procédure de décodage des instructions
    private String[] decode(String instr) {
        int cmpt = 0;
        int cursor = 0;
        String[] table = new String[10];
        for (int i = 1; i < instr.length(); i++)
            if (instr.charAt(i) == ' ') {
                table[cmpt] = instr.substring(cursor, i);
                cursor = i + 1;
                cmpt++;
            }
        table[cmpt] = instr.substring(cursor);
        return table;
    }

    int getHandSize() {
        return hand.size();
    }

    Card getCard(int handIndex) {
        return hand.get(handIndex);
    }

    // procédure appelée par le listener pour exécuter les instructions
    public void execute(String instr) {
        System.out.println("Execute : " + instr);
        String[] tableInstr = decode(instr);    // Tableau contenant les instructions
        StringBuilder answer = new StringBuilder();                     // réponse à la requête
        Integer temp = Integer.valueOf(tableInstr[0]);
        int opCode = temp.intValue();
        switch (opCode) {
            case 1:  // connexion acceptée => demande d'envoi d'informations sur le joueur
                // Syntaxe : 1 + id du joueur
                temp = Integer.valueOf(tableInstr[1]);
                playerId = temp.intValue();
                if (playerId != 0) {
                    players[playerId].setFirstName(players[0].getFirstName());
                    players[playerId].setLastName(players[0].getLastName());
                }
                players[playerId].setID(playerId);
                frame.setName(playerId, players[playerId].getFirstName());
                frame.setStatusBar("Connexion réussie");
                answer = new StringBuilder(tableInstr[1] + " " + players[playerId].getFirstName() + " " + players[playerId].getLastName());
                break;
            case 2:  // envoie les infos des autres joueurs
                // Syntaxe : 2 + id du joueur + prenom du joueur + nom du joueur
                temp = Integer.valueOf(tableInstr[1]);
                int i = temp.intValue();
                players[i].setFirstName(tableInstr[2]);
                players[i].setLastName(tableInstr[3]);
                players[i].setID(i);
                frame.setStatusBar(tableInstr[2] + " " + tableInstr[3] + " s'est connecté");
                frame.setName(i, tableInstr[2]);
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 3:  // demande de choisir le mode de choix des équipes
                // Syntaxe : 3
                DialogTeamChoice dtc = new DialogTeamChoice(frame, true);
                dtc.show();
                if (dtc.hasard) {
                    answer = new StringBuilder(String.valueOf(playerId) + " 1");  // choisir au hasard
                } else {
                    answer = new StringBuilder(String.valueOf(playerId) + " 0");  // choisir manuellement
                }
                break;
            case 4:  // demande de préparer le mode de tirage des équipes
                // Syntaxe : 4
                frame.prepareTeamChoice();
                frame.setStatusBar("Tirage des équipes...");
                answer = new StringBuilder(String.valueOf(playerId) + " 1");
                break;
            case 5:  // demande de choisir une carte
                // Syntaxe : 5
                frame.centerCanvas.setMode(2);
                frame.setStatusBar("Veuillez choisir une carte");
                break;
            case 6:  // donne la carte choisie par un joueur
                // Syntaxe : 6 + id joueur + position de la carte + numéro de la carte
                temp = Integer.valueOf(tableInstr[1]);
                int id = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                int pos = temp.intValue();
                temp = Integer.valueOf(tableInstr[3]);
                int nbr = temp.intValue();
                frame.teamChoiceShowCard(id, pos, nbr);
                frame.setStatusBar(players[id].getFirstName() + " a tiré une carte");
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 7:  // le tirage n'est pas bon, on recommence
                // Syntaxe : 7
                frame.prepareTeamChoice();
                frame.setStatusBar("Le tirage n'est pas bon...");
                answer = new StringBuilder(String.valueOf(playerId) + " 1");
                break;
            case 8:  // transmet le nouvel ordre des joueurs
                // Syntaxe : 9 + id1 + id2 + id3 + id4
                temp = Integer.valueOf(tableInstr[1]);
                int id1 = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                int id2 = temp.intValue();
                temp = Integer.valueOf(tableInstr[3]);
                int id3 = temp.intValue();
                temp = Integer.valueOf(tableInstr[4]);
                int id4 = temp.intValue();
                organisePlayers(id1, id2, id3, id4);
                frame.setScore(0, 0);  // initialize scores
                frame.setStatusBar("Equipes réorganisées");
                for (i = 0; i < 4; i++)
                    frame.setName(i, players[i].getFirstName());
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 9:  // demande de choisir son partenaire
                //Syntaxe : 9
                DialogPartnerChoice dpc = new DialogPartnerChoice(frame, true);
                for (i = 0; i < 4; i++)
                    if (i != playerId)
                        dpc.addPlayer(players[i].getFirstName());
                dpc.show();
                answer = new StringBuilder(String.valueOf(playerId) + " " + String.valueOf(dpc.number + 1));
                break;
            case 10: // donne les cartes
                // Syntaxe : 10 + carte1 + ... + carte9
                hand.clear();
                for (i = 1; i < 10; i++) {
                    hand.add(new Card(Integer.parseInt(tableInstr[i])));
                }
                quickSortCards(hand, 0, 8);
                for (i = 0; i < 4; i++)
                    if (i == playerId)
                        frame.setPlayerCards(hand);
                    else
                        frame.setOpponentCards(i, 9);
                frame.prepareMatch();         // prépare l'écran pour une nouvelle partie
                myAnouncement.clear();
                plieNbr = 0;
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 11: // demande de faire atout en premier
                // Syntaxe : 11
                DialogAtout da = new DialogAtout(frame, true, false);
                da.show();
                if (da.number == 4)
                    answer = new StringBuilder(String.valueOf(playerId) + " 4");
                else
                    answer = new StringBuilder(String.valueOf(playerId) + " " + String.valueOf(da.number));
                break;
            case 12: // demande de faire atout en second
                // Syntaxe : 12
                da = new DialogAtout(frame, true, true);
                da.show();
                answer = new StringBuilder(String.valueOf(playerId) + " " + String.valueOf(da.number));
                break;
            case 13: // communique l'atout
                // Syntaxe : 13 + numéro de l'atout + numero du joueur a faire
                //           atout
                temp = Integer.valueOf(tableInstr[1]);
                atout = temp.intValue();
                stoeck = Anouncement.findStoeck(hand, atout) ? 1 : 0;
                frame.lastPlieCanvas.setAtout(atout);
                frame.lastPlieCanvas.repaint();
                temp = Integer.valueOf(tableInstr[2]);
                frame.setAtout(temp.intValue());
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 14: // demande de jouer en premier
                // Syntaxe : 14
                frame.setStatusBar("A vous de jouer ...");
                frame.setAnounceEnabled(true);
                frame.playerCanvas.setMode(1);
                plieNbr++;
                break;
            case 15: // communique la carte jouée
                // Syntaxe : 15 + id du joueur + numéro de la carte
                temp = Integer.valueOf(tableInstr[1]);
                id = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                frame.showPlayedCard(id, temp.intValue());   // affiche la carte jouée
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 16: // demande de jouer ensuite
                // Syntaxe : 16 + highest + color + coupe      (coupe : 1 = true, 0 = false)
                temp = Integer.valueOf(tableInstr[1]);
                currentPlie.highest = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                currentPlie.color = temp.intValue();
                temp = Integer.valueOf(tableInstr[3]);
                if (temp.intValue() == 0)
                    currentPlie.cut = false;
                else
                    currentPlie.cut = true;
                frame.setStatusBar("A vous de jouer ...");
                frame.setAnounceEnabled(true);
                plieNbr++;
                frame.playerCanvas.setMode(2);  // let the player play
                break;
            case 17: // communique par qui la plie a été prise
                // Syntaxe : 17 + id du joueur
                temp = Integer.valueOf(tableInstr[1]);
                id = temp.intValue();
                frame.pickUpPlie(players[id].getFirstName());
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 18: // communique le résultat de la partie
                // Syntaxe : 18 + points de l'équipe + points de l'équipe adverse
                Integer sc1 = Integer.valueOf(tableInstr[1]);
                Integer sc2 = Integer.valueOf(tableInstr[2]);
                frame.setScore(sc1.intValue(), sc2.intValue());
                answer = new StringBuilder(String.valueOf(playerId) + " 1");  // signifie que l'opération s'est bien déroulée
                break;
            case 19: // demande de déclarer ses annonces
                // Syntaxe : 19
                answer = new StringBuilder(playerId + " " + myAnouncement.size());
                System.out.println("nbrAnounces : " + myAnouncement.size());

                for (var a : myAnouncement) {
                    System.out.println("type : " + a.getType() + " height : " + a.getCard());
                    answer.append(" ").append(a.getType()).append(" ").append(a.getCard().getNumber());
                }
                System.out.println("answer : " + answer);
                break;
            case 20: // transmet les annonces
                // Syntaxe : 20 + id joueur + nbr + annonces : (type , carte)
                // type :  0: stock, 1: 3cartes, 2: cinquante, 3: cent, 4: cent(carré), 5: centcinquante, 6: deux cent
                System.out.println("Traitement de l'instruction");
                temp = Integer.valueOf(tableInstr[1]);
                int player = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                nbr = temp.intValue();
                System.out.println("Création de la boîte de dialogue");
                DialogInfo di = new DialogInfo(frame, false);

                di.setText(0, players[player].getFirstName() + " annonce :");
                for (i = 0; i < nbr; i++) {
                    int type = Integer.parseInt(tableInstr[3 + i * 2]);
                    if (type == 0) {   // stock;
                        di.setText(i + 1, "Stöck");
                    } else {
                        Card card = new Card(Integer.parseInt(tableInstr[4 + i * 2]));
                        if (type < 4) {
                            di.setText(i + 1, Anouncement.NAMES[type] + " au " + card);
                        } else {
                            di.setText(i + 1, Anouncement.NAMES[type] + " des " + Card.RANK_NAMES[card.getRank()]);
                        }
                    }
                }
		/* temp = Integer.valueOf(tableInstr[3 + i * 2]);
		   if (temp.intValue() == 1)
		   di.setText(i+1, "stöck");*/
                // envoie la réponse avant d'afficher la boîte
                network.sendTo(String.valueOf(playerId) + " 1");
                di.show();
                // answer = String.valueOf(myPlayer) + " 1";
                // signifie que l'opération s'est bien déroulée
                break;
            case 21: // send the winner team
                // syntax: 21 + teamNbr + player1 + player2
                temp = Integer.valueOf(tableInstr[1]);
                int teamNbr = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                int p1 = temp.intValue();
                temp = Integer.valueOf(tableInstr[3]);
                int p2 = temp.intValue();
                DialogInfo diw = new DialogInfo(frame, false);
                diw.setText(0, "L'equipe numero " + String.valueOf(teamNbr + 1));
                diw.setText(1, players[p1].getFirstName() + " & " +
                        players[p2].getFirstName());
                diw.setText(2, "a gagne");
                frame.setStatusBar("Partie terminee");
                network.sendTo(String.valueOf(playerId) + " 1");
                diw.show();
                break;
            case 22: // ask if we want to do another game
                // syntax: 22
                DialogNewPart dnp = new DialogNewPart(frame, true);
                dnp.show();
                if (dnp.newPart)
                    answer = new StringBuilder(String.valueOf(playerId) + " 1");
                else
                    answer = new StringBuilder(String.valueOf(playerId) + " 0");
                break;
            case 23: // a player has left unexpectedly
                // syntax: 23 + player number
                di = new DialogInfo(frame, false);
                temp = Integer.valueOf(tableInstr[1]);
                id = temp.intValue();
                di.setText(0, players[id].getFirstName() + " a quitte le jeu.");
                di.setText(1, "La partie est donc terminee");
                frame.setStatusBar("Partie interrompue");
                disconnect();
                frame.disconnect();
                di.show();
        }
        if (answer.toString() != "")
            network.sendTo(answer.toString());
    }

    // organise les joueurs dans le bon ordre
    private void organisePlayers(int id1, int id2, int id3, int id4) {
        var p = new ClientPlayer[4];
        p[0] = new ClientPlayer(players[id1]);
        p[1] = new ClientPlayer(players[id2]);
        p[2] = new ClientPlayer(players[id3]);
        p[3] = new ClientPlayer(players[id4]);
        int myNewPlayer = 0;
        for (int i = 0; i < 4; i++) {
            players[i] = p[i];
            if (playerId == p[i].getID())
                myNewPlayer = i;
            players[i].setID(i);
        }
        playerId = myNewPlayer;
    }


    // Super algorithme QuickSort
    void quickSortCards(List<Card> array, int min, int max) {
        int i = min;
        int j = max;

        int x = array.get((min + max) / 2).getNumber();
        do {
            while (array.get(i).getNumber() < x)
                i++;
            while (x < array.get(j).getNumber())
                j--;
            if (i <= j) {
                Collections.swap(array, i, j);
                i++;
                j--;
            }
        } while (i <= j);
        if (min < j)
            quickSortCards(array, min, j);
        if (i < max)
            quickSortCards(array, i, max);
    }

    // communique la carte choisie lors du tirage des équipes
    // communique également la carte jouée lors de la partie
    // Syntaxe : id + numéro de la carte + points de la carte + annonces?? (0 rien, 1 annonces, 2 stöck, 3 annonces & stock)
    void sendCard(int cardNumber, int score) {
        int annoucementCode = 0;
        var kingAtout = new Card(atout, Card.RANK_ROI);
        var queenAtout = new Card(atout, Card.RANK_DAME);
        if ((cardNumber == (atout * 9 + 6)) || (cardNumber == (atout * 9 + 7))) {
            if ((stoeck == 1) || (stoeck == 2))
                stoeck++;
            if ((stoeck == 3) && (!myAnouncement.isEmpty())) {
                annoucementCode = 2;         // annonce le stöck lorsqu'on pose la dernière carte
                stoeck = 0;
                System.out.println("Annonce : stock");
                myAnouncement.clear();
            }
        }
        if (!myAnouncement.isEmpty() && (plieNbr == 1)) {  // on annonce à la première plie
            if ((stoeck > 0) && (myAnouncement.size() > 1)) {
                annoucementCode = 3;
                stoeck = 0;
                System.out.println("Annonce : annonce + stock");
                // TODO: fix
                //nbrAnounces--;    // le stöck peut être décomptabilisé
            } else if (!myAnouncement.isEmpty() && (stoeck == 0)) {
                annoucementCode = 1;
                System.out.println("Annonce : annonce");
            }
        }

        network.sendTo(playerId + " " + cardNumber + " " + score + " " + annoucementCode);
        // TODO: fix
        // nbrAnounces = 0;  // reset anounces number
    }


    // détermine si on peut jouer la carte choisie
    public int playCard(int mode, Card cardChoosen) {
        int answer = 0;    // joue
        if (mode == 2) {
            if (cardChoosen.getColor() != currentPlie.color) {
                if (cardChoosen.getColor() == atout) {
                    if (currentPlie.cut)
                        switch (cardChoosen.getRank()) {
                            case Card.RANK_BOURG:
                                answer = 0;            // bourg : ok!
                                break;
                            case Card.RANK_NELL:
                                if (currentPlie.highest != Card.RANK_BOURG)
                                    answer = 0;       // nell sans bourg : ok!
                                else
                                    answer = -2;     // on ne peut pas sous-couper
                                break;
                            default:
                                if (((currentPlie.highest == Card.RANK_BOURG) || (currentPlie.highest == Card.RANK_NELL)) ||
                                        (currentPlie.highest > cardChoosen.getRank()))
                                    answer = -2;  // on ne peut pas sous-couper
                                else
                                    answer = 0;        // sur-coupe : ok !
                        }
                    else
                        answer = 0;   // coupe : ok!
                } else {    // carte jouée pas d'atout
                    if (checkColor(currentPlie.color)) {
                        // The player owns cards from the required color
                        if ((currentPlie.color == atout) && (bourgSec()))
                            answer = 0; // bourg sec -> ok
                        else
                            answer = -1;    // il faut suivre
                    } else
                        answer = 0;     // pas de cette couleur : ok!
                }
            } else
                answer = 0;     // suivi : ok!
        }
        return answer;
    }


    // vérifie si le joueur possède la couleur demandée
    boolean checkColor(int colorChecked) {
        for (var card : hand)
            if (card.getColor() == colorChecked)
                return true;
        return false;
    }

    // cherche si on a le stöck
    int findStoeck() {
        int queen = atout * 9 + 6;
        int king = atout * 9 + 7;
        for (var card : hand) {
            if (card.getNumber() == queen) {
                for (var secondCard : hand) {
                    if (secondCard.getNumber() == king) {
                        System.out.println("Stoeck !!");
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

    void findAnouncement() {
        myAnouncement.clear();
        myAnouncement.addAll(Anouncement.findAnouncements(hand));
        if (stoeck > 0) {
            myAnouncement.add(new Anouncement(Anouncement.STOECK, null));
        }
    }


    /**
     * Checks if we have "bourg sec"
     */
    boolean bourgSec() {
        boolean bourg = false;
        int atoutNbr = 0;
        for (var card : hand) {
            if (card.getColor() == atout) {
                atoutNbr++;
                if (atoutNbr > 1) {
                    return false;
                }
                if (card.getRank() == Card.RANK_BOURG) // bourg
                    bourg = true;
            }
        }
        return bourg && (atoutNbr == 1);
    }

    /**
     * Disconect from the server
     */
    public void disconnect() {
        listener.stop = true;
        network.disconnect();
    }

    public int getPlayerId() { return playerId; }

    public int getAtout() { return atout; }
}

// ********************** CLASS TMember ****************************************
class Member {
    // Variables
    private String firstName;
    private String lastName;
    private String function;

    // Constructeur
    public Member() {
    }

    public Member(String firstName, String lastName, String function) {
        this();
        this.firstName = firstName;
        this.lastName = lastName;
        this.function = function;
    }

    // Getters
    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFunction() {
        return function;
    }

    // Setters
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setFunction(String function) {
        this.function = function;
    }
}

// **************************** CLASS leflat.jass.server.Player ***********************************
class ClientPlayer extends Member {
    // Variables
    private int id;        // numéro d'identification du joueur

    public ClientPlayer() {
        super();
    }

    public ClientPlayer(ClientPlayer copyPlayer) {
        super(copyPlayer.getFirstName(), copyPlayer.getLastName(),
                copyPlayer.getFunction());
        this.id = copyPlayer.getID();
    }

    // Getter
    public int getID() {
        return id;
    }

    // Setter
    public void setID(int iD) {
        this.id = iD;
    }
}


// **************************** CLASS Card *************************************
/*
abstract class Card {
    static final int[] value = {0,0,0,0,10,2,3,4,11};     // valeurs des cartes
    static final int[] valueAtout = {0,0,0,14,10,20,3,4,11};
    static final String[] color = {"pique", "coeur", "carreau", "trèfle"};
    static final String[] name = {"six", "sept", "huit", "neuf", "dix", "bourg", "dame", "roi", "as"};
    static final String[] anounce = {"stöck", "3 cartes", "cinquante", "cent", "cent", "cent cinquante", "deux cents"};

    public static int getColor(int card) {
	return card / 9;
    }

    public static int getHeight(int card) {
	return card % 9;
    }
}

 */


// **************************** CLASS leflat.jass.server.Plie *************************************
/*
class Plie {
    public int highest;       // plus haute carte (si coupé, plus haut atout)
    public int color;         // couleur demandée
    public boolean coupe;     // coupé ou pas
}
*/

// **************************** CLASS Anounce **********************************
/*
class Anounce {
    int type;     // 0: stöck, 1: 3 cartes, 2: cinquante, 3: cent, 4: carré
    int height;   // hauteur de l'annonce
}

 */
