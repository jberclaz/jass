/*
 * FlatJassClientSystem.java
 *
 * Created on 18. avril 2000, 16:09
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version 1.2
 */
import java.net.*;

public class FlatJassClientSystem extends Object {
    // Variables
    ClientFrame frame = new ClientFrame(this);

    // Variables du moteur de jeu
    ClientListener listener;                       // classe qui écoute
    ClientNetwork network = new ClientNetwork();   // classe qui implémente socket
    public int myPlayer;                           // id du joueur
    leflat.jass.server.Player[] players = new leflat.jass.server.Player[4];              // les 4 joueurs
    int[] cards = new int[9];                      // main du joueur
    int atout;                                     // atout
    leflat.jass.server.Plie currentPlie = new leflat.jass.server.Plie();                 // plie en cours
    Anounce[] myAnounces = new Anounce[3];         // annonces
    int nbrAnounces;                               // nombre d'annonces
    int stock;                                     // 0:rien, 1:stöck, 2: joué un, 3:joué les deux
    int plieNbr;                                   // numéro de la plie

    /** Creates new FlatJassClientSystem */
    public FlatJassClientSystem() {
	for (int i=0; i<4; i++)             // construire les objets player
	    players[i] = new leflat.jass.server.Player();
	for (int i=0; i<3; i++)             // construire les objets Anounce
	    myAnounces[i] = new Anounce();
	frame.show();
    }

    /**
  * @param args the command line arguments
  */
    public static void main (String args[]) {
	new FlatJassClientSystem();
    }

    public int connect(String firstName, String lastName, String IP) {
	// renvoie -1 en cas d'échec et 0 si réussi
	System.out.println("Connected to " + IP);
	Socket cs=network.connect(IP);
	if (cs != null) {         // connexion réussie
	    players[0].setFirstName(firstName);
	    players[0].setLastName(lastName);
	    if (listener == null) {
		listener = new ClientListener(this,cs);
		System.out.println("Création d'un nouveau listener"); // débuggage
	    }
	    else {
		// listener.setSocket(cs);
		// listener.stop = false;
		listener = new ClientListener(this,cs);
	    }
	    listener.start();
	}
        else {
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
	for (int i=1; i<instr.length(); i++)
	    if (instr.charAt(i) == ' ') {
		table[cmpt] = instr.substring(cursor, i);
		cursor = i + 1;
		cmpt++;
	    }
	table[cmpt] = instr.substring(cursor, instr.length());
	return table;
    }

    // procédure appelée par le listener pour exécuter les instructions
    public void execute(String instr) {
	System.out.println("Execute : " + instr);
	String[] tableInstr = decode(instr);    // Tableau contenant les instructions
	String answer = "";                     // réponse à la requête
	Integer temp = Integer.valueOf(tableInstr[0]);
	int opCode = temp.intValue();
	switch (opCode) {
	    case 1 :  // connexion acceptée => demande d'envoi d'informations sur le joueur
                // Syntaxe : 1 + id du joueur
                temp = Integer.valueOf(tableInstr[1]);
                myPlayer = temp.intValue();
                if (myPlayer != 0) {
		    players[myPlayer].setFirstName(players[0].getFirstName());
		    players[myPlayer].setLastName(players[0].getLastName());
                }
                players[myPlayer].setID(myPlayer);
                frame.setName(myPlayer, players[myPlayer].getFirstName());
                frame.setStatusBar("Connexion réussie");
                answer = tableInstr[1] + " " + players[myPlayer].getFirstName() + " " + players[myPlayer].getLastName();
                break;
	    case 2 :  // envoie les infos des autres joueurs
                // Syntaxe : 2 + id du joueur + prenom du joueur + nom du joueur
                temp = Integer.valueOf(tableInstr[1]);
                int i = temp.intValue();
                players[i].setFirstName(tableInstr[2]);
                players[i].setLastName(tableInstr[3]);
                players[i].setID(i);
                frame.setStatusBar(tableInstr[2] + " " + tableInstr[3] + " s'est connecté");
                frame.setName(i, tableInstr[2]);
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 3 :  // demande de choisir le mode de choix des équipes
                // Syntaxe : 3
                DialogTeamChoice dtc = new DialogTeamChoice(frame, true);
                dtc.show();
                if (dtc.hasard) {
		    answer = String.valueOf(myPlayer) + " 1";  // choisir au hasard
                }
                else {
		    answer = String.valueOf(myPlayer) + " 0";  // choisir manuellement
                }
                break;
	    case 4 :  // demande de préparer le mode de tirage des équipes
                // Syntaxe : 4
                frame.prepareTeamChoice();
                frame.setStatusBar("Tirage des équipes...");
                answer = String.valueOf(myPlayer) + " 1";
                break;
	    case 5 :  // demande de choisir une carte
                // Syntaxe : 5
                frame.centerCanvas.mode = 2;
                frame.setStatusBar("Veuillez choisir une carte");
                break;
	    case 6 :  // donne la carte choisie par un joueur
                // Syntaxe : 6 + id joueur + position de la carte + numéro de la carte
                temp = Integer.valueOf(tableInstr[1]);
                int id = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                int pos = temp.intValue();
                temp = Integer.valueOf(tableInstr[3]);
                int nbr = temp.intValue();
                frame.teamChoiceShowCard(id, pos, nbr);
                frame.setStatusBar(players[id].getFirstName() + " a tiré une carte");
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 7 :  // le tirage n'est pas bon, on recommence
                // Syntaxe : 7
                frame.prepareTeamChoice();
                frame.setStatusBar("Le tirage n'est pas bon...");
                answer = String.valueOf(myPlayer) + " 1";
                break;
	    case 8 :  // transmet le nouvel ordre des joueurs
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
                for (i=0; i<4; i++)
		    frame.setName(i, players[i].getFirstName());
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 9 :  // demande de choisir son partenaire
                //Syntaxe : 9
                DialogPartnerChoice dpc = new DialogPartnerChoice(frame, true);
                for (i=0; i<4; i++)
		    if (i != myPlayer)
			dpc.addPlayer(players[i].getFirstName());
                dpc.show();
                answer = String.valueOf(myPlayer) + " " + String.valueOf(dpc.number + 1);
                break;
	    case 10 : // donne les cartes
                // Syntaxe : 10 + carte1 + ... + carte9
                for (i=1; i<10; i++) {
		    temp = Integer.valueOf(tableInstr[i]);
		    cards[i-1] = temp.intValue();
                }
                quickSort(cards, 0, 8);
                for (i=0; i<4; i++)
		    if (i == myPlayer)
			frame.setPlayerCards(cards);
		    else
			frame.setOpponentCards(i, 9);
                frame.prepareMatch();         // prépare l'écran pour une nouvelle partie
                nbrAnounces = 0;
                plieNbr = 0;
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 11 : // demande de faire atout en premier
                // Syntaxe : 11
                DialogAtout da = new DialogAtout(frame, true, false);
                da.show();
                if (da.number == 4)
		    answer = String.valueOf(myPlayer) + " 4";
                else
		    answer = String.valueOf(myPlayer) + " " + String.valueOf(da.number);
                break;
	    case 12 : // demande de faire atout en second
                // Syntaxe : 12
                da = new DialogAtout(frame, true, true);
                da.show();
                answer = String.valueOf(myPlayer) + " " + String.valueOf(da.number);
                break;
	    case 13 : // communique l'atout
                // Syntaxe : 13 + numéro de l'atout + numero du joueur a faire
		//           atout
                temp = Integer.valueOf(tableInstr[1]);
                atout = temp.intValue();
                stock = findStock();
                frame.lastPlieCanvas.atout = atout;
                frame.lastPlieCanvas.repaint();
		temp = Integer.valueOf(tableInstr[2]);
		frame.setAtout(temp.intValue());
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 14 : // demande de jouer en premier
                // Syntaxe : 14
                frame.setStatusBar("A vous de jouer ...");
                frame.setAnounceEnabled(true);
                frame.playerCanvas.setMode(1);
                plieNbr++;
                break;
	    case 15 : // communique la carte jouée
                // Syntaxe : 15 + id du joueur + numéro de la carte
                temp = Integer.valueOf(tableInstr[1]);
                id = temp.intValue();
                temp = Integer.valueOf(tableInstr[2]);
                frame.showPlayedCard(id, temp.intValue());   // affiche la carte jouée
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 16 : // demande de jouer ensuite
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
	    case 17 : // communique par qui la plie a été prise
                // Syntaxe : 17 + id du joueur
                temp = Integer.valueOf(tableInstr[1]);
                id = temp.intValue();
                frame.pickUpPlie(players[id].getFirstName());
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 18 : // communique le résultat de la partie
                // Syntaxe : 18 + points de l'équipe + points de l'équipe adverse
                Integer sc1 = Integer.valueOf(tableInstr[1]);
                Integer sc2 = Integer.valueOf(tableInstr[2]);
                frame.setScore(sc1.intValue(), sc2.intValue());
                answer = String.valueOf(myPlayer) + " 1";  // signifie que l'opération s'est bien déroulée
                break;
	    case 19 : // demande de déclarer ses annonces
                // Syntaxe : 19
                answer = String.valueOf(myPlayer) + " " + String.valueOf(nbrAnounces);
                System.out.println("nbrAnounces : " + nbrAnounces);

                for (i=0; i<nbrAnounces; i++) {
		    System.out.println("type : " + myAnounces[i].type + " height : " + myAnounces[i].height);
		    answer = answer + " " + String.valueOf(myAnounces[i].type) + " " + String.valueOf(myAnounces[i].height);
                }
                System.out.println("answer : " + answer);
                break;
	    case 20 : // transmet les annonces
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
                int type;
                int height;
                for (i=0; i<nbr; i++) {
		    temp = Integer.valueOf(tableInstr[3 + i * 2]);
		    type = temp.intValue();
		    temp = Integer.valueOf(tableInstr[4 + i * 2]);
		    height = temp.intValue();
		    if (type == 0)	// stock;
                  	di.setText(i+1, "Stöck");
		    else	if (type < 4)
			di.setText(i+1, Card.anounce[type] + " au " + Card.name[Card.getHeight(height)] + " de " + Card.color[Card.getColor(height)]);
		    else
			di.setText(i+1, Card.anounce[type] + " des " + Card.name[Card.getHeight(height)]);
                }
		/* temp = Integer.valueOf(tableInstr[3 + i * 2]);
		   if (temp.intValue() == 1)
		   di.setText(i+1, "stöck");*/
		// envoie la réponse avant d'afficher la boîte
		network.sendTo(String.valueOf(myPlayer) + " 1");
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
		diw.setText(0, "L'equipe numero " + String.valueOf(teamNbr+1));
		diw.setText(1, players[p1].getFirstName() + " & " +
			    players[p2].getFirstName());
		diw.setText(2, "a gagne");
		frame.setStatusBar("Partie terminee");
		network.sendTo(String.valueOf(myPlayer) + " 1");
		diw.show();
		break;
	    case 22: // ask if we want to do another game
		// syntax: 22
		DialogNewPart dnp = new DialogNewPart(frame, true);
		dnp.show();
		if (dnp.newPart)
		    answer = String.valueOf(myPlayer) + " 1";
		else
		    answer = String.valueOf(myPlayer) + " 0";
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
	if (answer != "")
	    network.sendTo(answer);
    }

    // organise les joueurs dans le bon ordre
    private void organisePlayers(int id1, int id2, int id3, int id4) {
	leflat.jass.server.Player[] p = new leflat.jass.server.Player[4];
	p[0] = new leflat.jass.server.Player(players[id1]);
	p[1] = new leflat.jass.server.Player(players[id2]);
	p[2] = new leflat.jass.server.Player(players[id3]);
	p[3] = new leflat.jass.server.Player(players[id4]);
	int myNewPlayer = 0;
	for (int i=0; i<4; i++) {
	    players[i] = p[i];
	    if (myPlayer == p[i].getID())
		myNewPlayer = i;
	    players[i].setID(i);
	}
	myPlayer = myNewPlayer;
    }


    // Super algorithme QuickSort
    int[] quickSort(int[] tab, int min, int max) {
	int i = min;
	int j = max;
	int y;

	int x = tab[(min + max) / 2];
	do {
	    while (tab[i] < x)
		i++;
	    while (x < tab[j])
		j--;
	    if (i <= j) {
		y = tab[i];
		tab[i] = tab[j];
		tab[j] = y;
		i++;
		j--;
	    }
	} while (i <= j);
	if (min < j)
	    tab = quickSort(tab, min, j);
	if (i < max)
	    tab = quickSort(tab, i, max);
	return tab;
    }

    // communique la carte choisie lors du tirage des équipes
    // communique également la carte jouée lors de la partie
    // Syntaxe : id + numéro de la carte + points de la carte + annonces?? (0 rien, 1 annonces, 2 stöck, 3 annonces & stock)
    void sendCard(int cardNumber, int score) {
	int sendAnounces = 0;
	if ((cardNumber == (atout * 9 + 6)) || (cardNumber == (atout * 9 + 7))) {
	    if ((stock == 1) || (stock == 2))
		stock++;
	    if ((stock == 3) && (nbrAnounces > 0)) {
		sendAnounces = 2;         // annonce le stöck lorsqu'on pose la dernière carte
		stock = 0;
		System.out.println("Annonce : stock");
		nbrAnounces = 0;
	    }
	}
	if ((nbrAnounces > 0) && (plieNbr == 1)) {  // on annonce à la première plie
	    if ((stock > 0) && (nbrAnounces > 1)) {
		sendAnounces = 3;
		stock = 0;
		System.out.println("Annonce : annonce + stock");
		nbrAnounces--;    // le stöck peut être décomptabilisé
	    }
	    else if ((nbrAnounces > 0) && (stock == 0)) {
		sendAnounces = 1;
		System.out.println("Annonce : annonce");
	    }
	}

	network.sendTo(String.valueOf(myPlayer) + " " + String.valueOf(cardNumber) + " " + String.valueOf(score) + " " + String.valueOf(sendAnounces));
	nbrAnounces = 0;  // reset anounces number
    }


    // détermine si on peut jouer la carte choisie
    public int playCard(int mode, int cardChoosen) {
	int answer = 0;    // joue
	if (mode == 2) {
	    if (Card.getColor(cardChoosen) != currentPlie.color) {
		if (Card.getColor(cardChoosen) == atout) {
		    if (currentPlie.cut)
			switch (Card.getHeight(cardChoosen)) {
			    case 5 :  answer = 0;            // bourg : ok!
				break;
			    case 3 :  if (currentPlie.highest != 5)
				answer = 0;       // nell sans bourg : ok!
			    else
				answer = -2;     // on ne peut pas sous-couper
			    break;
			    default : if (((currentPlie.highest == 5) || (currentPlie.highest == 3)) ||
					  (currentPlie.highest > Card.getHeight(cardChoosen)))
				answer = -2;  // on ne peut pas sous-couper
			    else
				answer = 0;        // sur-coupe : ok !
			}
		    else
			answer = 0;   // coupe : ok!
		}
		else {    // carte jouée pas d'atout
		    if (checkColor(currentPlie.color)) {
			// The player owns cards from the required color
			if ((currentPlie.color == atout) && (bourgSec()))
			    answer = 0; // bourg sec -> ok
			else
			    answer = -1;    // il faut suivre
		    }
		    else
			answer = 0;     // pas de cette couleur : ok!
		}
	    }
	    else
		answer = 0;     // suivi : ok!
	}
	return answer;
    }


    // vérifie si le joueur possède la couleur demandée
    boolean checkColor(int colorChecked) {
	boolean present = false;
	for (int i=0; i<9; i++)
	    if (Card.getColor(cards[i]) == colorChecked)
		present = true;
	return present;
    }

    // cherche si on a le stöck
    int findStock() {
	int queen = atout * 9 + 6;
	int king = atout * 9 + 7;
	int stock = 0;
	for (int i=0; i<9; i++)
	    if (cards[i] == queen)
		for (int j=0; j<9; j++)
		    if (cards[j] == king)
			stock = 1;
	if (stock == 1)
	    System.out.println("Stock !!");
	return stock;
    }

    void findAnounce() {
	// type :  0: stock, 1: 3cartes, 2: cinquante, 3: cent, 4: cent(carré), 5: centcinquante, 6: deux cent
	nbrAnounces = 0;
	// cherche les carrés
	int i=0;
	int nbrCards;
	while ((i<9) && ((cards[i] / 9) == 0)) {   // tant que c'est du pique (couleur la + à gauche)
	    nbrCards = 1;
	    for (int j=i+1; j<9; j++) {
		if ((cards[j] % 9) == (cards[i] % 9))
		    nbrCards++;
	    }
	    if ((nbrCards == 4) && ((cards[i] % 9) > 2)) {       // carré trouvé
		if ((cards[i] % 9) == 3)		// cent-cinquante
		    myAnounces[nbrAnounces].type = 5;
		else if ((cards[i] % 9) == 5)	// deux cents
		    myAnounces[nbrAnounces].type = 6;
		else				// cent
		    myAnounces[nbrAnounces].type = 4;
		myAnounces[nbrAnounces].height = cards[i] % 9;
		nbrAnounces++;
		System.out.println("Carré trouvé : " + cards[i] % 9);
	    }
	    i++;
	}

	// cherche les suites
	for (i=0; i<7; i++) {
	    int color = Card.getColor(cards[i]);
	    int j = i + 1;
	    nbrCards = 1;
	    while ((j < 9) && ((cards[j] / 9) == color)) {
		if (cards[j] == (cards[i] + j - i)) // si les cartes se suivent
		    nbrCards++;
		j++;
	    }
	    if (nbrCards > 2) {   // on a trouvé une suite
		if (nbrCards > 5)
		    nbrCards = 5;
		myAnounces[nbrAnounces].type = nbrCards - 2;
		myAnounces[nbrAnounces].height = cards[i + nbrCards - 1];
		nbrAnounces++;
		System.out.println("Suite trouvée : " + cards[i + nbrCards - 1] + " type : " + (nbrCards - 2));
		i = j-1;
	    }
	}

	// Stöck
	if (stock > 0)
	    nbrAnounces++;
    }


    /**
     * Checks if we have "bourg sec"
     */
    boolean bourgSec() {
	boolean bourg = false;
	int atoutNbr = 0;
	for (int i=0; i<9; i++)
	    if (Card.getColor(cards[i]) == atout) {
		atoutNbr++;
		if (Card.getHeight(cards[i]) == 5) // bourg
		    bourg = true;
	    }
	if (bourg && (atoutNbr == 1))
	    return true;
	else
	    return false;
    }

    /**
     * Disconect from the server
     */
    public void disconnect() {
	listener.stop = true;
	network.disconnect();
    }
}

// ********************** CLASS TMember ****************************************
class TMember {
    // Variables
    private String firstName;
    private String lastName;
    private String function;

    // Constructeur
    public TMember() {
    }

    public TMember(String firstName, String lastName, String function) {
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
class Player extends TMember {
    // Variables
    private int iD;		// numéro d'identification du joueur

    public Player() {
	super();
    }

    public Player(leflat.jass.server.Player copyPlayer) {
	super(copyPlayer.getFirstName(), copyPlayer.getLastName(),
	      copyPlayer.getFunction());
	this.iD = copyPlayer.getID();
    }

    // Getter
    public int getID() {
	return iD;
    }

    // Setter
    public void setID(int iD) {
	this.iD = iD;
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
class Plie {
    public int highest;       // plus haute carte (si coupé, plus haut atout)
    public int color;         // couleur demandée
    public boolean coupe;     // coupé ou pas
}


// **************************** CLASS Anounce **********************************
class Anounce {
    int type;     // 0: stöck, 1: 3 cartes, 2: cinquante, 3: cent, 4: carré
    int height;   // hauteur de l'annonce
}
