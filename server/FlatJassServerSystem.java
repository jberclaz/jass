//Title:        FlatJassServer
//Version:      1.2
//Copyright:    Copyright (c) 1998
//Author:       Pierre M�trailler & J�rome Berclaz
//Company:      Flat(r)
//Description:  This is the server for the Jass Card Game made by and
//              for the proud members of the FLAT(r)
//
//              Long life to the FLAT(r)!


// package FlatJassServerProject;
import java.net.*;
import java.io.*;
import java.util.*;

public class FlatJassServerSystem {
    private int atout;
    private int firstToPlay;   // celui qui commence la partie et fait atout
    private int playersConnected;    // nombre de joueurs connect�s
    private Plie currentPlie;  // plie en cours
    private Player[] players = new Player[4]; // les 4 joueurs
    private Team[] teams = new Team[2];      // les 2 �quipes
  
    // ***************** PROVISOIRE ****************************************
    // static final int[] cards = {0,1,2,3,8,10,15,16,18, 11,12,13,17,19,20,21,30,34, 
    //   				14,22,23,25,28,29,31,35,32, 4,5,6,7,33,9,24,26,27};
    //**********************************************************************

    int          port_num;
    ServerSocket myServerSocket=null;
    ServerNetwork[] myServerNetwork = new ServerNetwork[4];


    public FlatJassServerSystem() {
	this(32107);
    }

    public FlatJassServerSystem(int port) {
	port_num = port;

	for (int i=0; i<4; i++)       // cr�ation des joueurs
	    players[i] = new Player();
	for (int i=0; i<2; i++)       // cr�ation des �quipes
	    teams[i] = new Team();
	currentPlie = new Plie();

	System.out.println("Flat Jass System Server");
	System.out.println("Version 1.2");
	System.out.println("(c) 2000-2002 by FLAT(r)");System.out.println();
	playersConnected = 0;

	// Create server socket
	try {
	    myServerSocket = new ServerSocket(port_num);

	    System.out.println("Server socket created on port "+ port_num);
	}
	catch (IOException e) {
	    System.out.println("ERROR: cannot create server socket");
	    System.exit(1);
	}

	do {

	    try {
		while (playersConnected < 4) {  // attend 4 connexions
		    playersConnected = waitConnect();
		}
		
		Integer temp;
		String[] instr = new String[10];
	    
		do {
		    chooseTeam();     // d�termine les �quipes
		    
		    // Play one game (until 1500) 
		    playPart();
		    
		    // ask whether they want to play another part
		    myServerNetwork[0].sendStr("22");
		    
		    instr = decode(myServerNetwork[0].rcvStr()); // r�ponse
		    temp = Integer.valueOf(instr[1]);
		    
		    teams[0].resetScore();
		    teams[1].resetScore();
		} while (temp.intValue() != 0);
		
	    } catch (ClientLeftException e) {
		int id = e.getClientId();
		for (int i=0; (i<=playersConnected) && (i!=4); i++)
		    if (i != id)
			myServerNetwork[i].sendStr("23 "+String.valueOf(id));

		System.out.println("sleep...");
		try {
		    Thread.sleep(2000);
		} catch (InterruptedException ex) {
		}
		System.out.println("Wakeup...");
	    }
	    
	    // close sockets and remove players
	    for(int i=0; (i<=playersConnected) && (i!=4); i++)
		myServerNetwork[i].close();
	    playersConnected = 0;
	} while(true); //  loop forever
    }    

    // attend la connexion d'un joueur
    public int waitConnect() throws ClientLeftException{
	int newPlayers = playersConnected;
	if (myServerNetwork[playersConnected] == null)
	    myServerNetwork[playersConnected]=new ServerNetwork();
	if (myServerNetwork[playersConnected].connect(myServerSocket)) {  
	    // la connexion a r�ussi

	    myServerNetwork[playersConnected].setClientId(playersConnected);
	    myServerNetwork[playersConnected].sendStr("1 " + String.valueOf(playersConnected)); // donne son id et demande des infos
	    String[] instr = new String[10];
	    instr = decode(myServerNetwork[playersConnected].rcvStr());  // attend les infos

	    players[playersConnected].firstName = instr[1];
	    players[playersConnected].lastName = instr[2];
	    players[playersConnected].iD = playersConnected;

	    System.out.println(instr[1] + " " +instr[2] + " is connected");
	
	    for (int i=0; i<playersConnected; i++) {
		// infos SUR LES joueurs d�j� connect�s
		myServerNetwork[playersConnected].sendStr("2 " + String.valueOf(i) + " " + players[i].firstName + " " + players[i].lastName);
		instr[1] = myServerNetwork[playersConnected].rcvStr(); // attend la r�ponse

		// infos AUX joueurs d�j� connect�s
		myServerNetwork[i].sendStr("2 " + String.valueOf(playersConnected) + " " + players[playersConnected].firstName + " " + players[playersConnected].lastName);
		instr[1] = myServerNetwork[i].rcvStr(); // attend la r�ponse
	    }
	    newPlayers++;      // incr�mente le nombre de joueurs connect�s
	}
	return newPlayers;
    }

    public static void main(String[] args) {
	Integer temp = null;
	if (args.length > 1) 
	    if (args[0].compareTo("-p") == 0) 
		temp = Integer.valueOf(args[1]);
	    else {
		System.out.println("Syntax : java FlatJassServerSystem -p <port_number>");
		System.exit(-1);
	    }
	FlatJassServerSystem flatJassServerSystem;
	if (temp == null)
	    flatJassServerSystem = new FlatJassServerSystem();
	else
	    flatJassServerSystem = new FlatJassServerSystem(temp.intValue());
//  flatJassServerSystem.invokedStandalone = true;
    }
    private boolean invokedStandalone = false;


    // Proc�dure de d�codage des instructions
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
  
  
    // Proc�dure de d�codage des instructions en integer
    private int[] decodint(String instr) {
	int cmpt = 0;
	int cursor = 0;
	Integer temp;
	int[] table = new int[10];
	for (int i=1; i<instr.length(); i++)
	    if (instr.charAt(i) == ' ') {
		temp = Integer.valueOf(instr.substring(cursor, i));
		table[cmpt] = temp.intValue();
		cursor = i + 1;
		cmpt++;
	    }
	temp = Integer.valueOf(instr.substring(cursor, instr.length()));  
	table[cmpt] = temp.intValue();
	return table;
    }
  

    private void chooseTeam() throws ClientLeftException{
	myServerNetwork[0].sendStr("3");    // demande de choisir le mode de tirage des �quipes
	String[] instr = new String[10];
	instr = decode(myServerNetwork[0].rcvStr()); // r�ponse
	Integer temp = Integer.valueOf(instr[1]);
	if (temp.intValue() == 1) { // choisir au hasard
	    for (int i=0; i<4; i++) {       // i<4 normalement !!!
		myServerNetwork[i].sendStr("4");    // pr�paration du tirage des �quipes
		instr[1] = myServerNetwork[i].rcvStr(); //r�ponse
	    }
	    int[] cards = new int[36];
	    boolean tirageOk = false;
	    int[] cardsChoosen = new int[4];    // cartes tir�es
	    do {
		cards = chooseCards();
		for (int i=0; i<4; i++) {
		    myServerNetwork[i].sendStr("5");  // demande de choisir une carte
		    instr = decode(myServerNetwork[i].rcvStr());  //r�ponse
		  
		    temp = Integer.valueOf(instr[1]);
		    cardsChoosen[i] = cards[temp.intValue()];
		    for (int j=0; j<4; j++) {    // communique la carte choisie
			myServerNetwork[j].sendStr("6 "+ String.valueOf(i) +" "+ instr[1] +" "+ String.valueOf(cardsChoosen[i]));
			instr[3] = myServerNetwork[j].rcvStr();
		    }
		}
		// delay to allow players to watch cards
		try {
		    Thread.sleep(2000);
		} catch (InterruptedException e) {
		}
	      
		// d�termine les �quipes
		tirageOk = calculateTeam(cardsChoosen);
		if (!tirageOk)
		    for (int i=0; i<4; i++) {
			myServerNetwork[i].sendStr("7"); // le tirage n'est pas bon : on recommence
			instr[9] = myServerNetwork[i].rcvStr();
		    }
	    } while (!tirageOk);
	}
	else {       // choisir son partenaire
	    myServerNetwork[0].sendStr("9");    // demande de choisir le partenaire
	    instr = decode(myServerNetwork[0].rcvStr()); // r�ponse
	    teams[0].players[0] = players[0];
	    players[0].myTeam = 0;
	    int j = 0;
	    temp = Integer.valueOf(instr[1]);
	    for (int i=1; i<4; i++)
		if (i == temp.intValue()) {
		    teams[0].players[1] = players[i];
		    players[i].myTeam = 0;
		}
		else {
		    teams[1].players[j] = players[i];
		    players[i].myTeam = 1;
		    j++;
		}
	}
	organisePlayers();
	for (int i=0; i<4; i++) {  // transmet le nouvel ordre aux clients
	    myServerNetwork[i].sendStr("8 "+String.valueOf(players[0].iD)+" "+String.valueOf(players[1].iD)+" "
				       +String.valueOf(players[2].iD)+" "+String.valueOf(players[3].iD));
	    instr[2] = myServerNetwork[i].rcvStr();
	}


	// reorganize IDs correctly
	for (int i=0; i<4;i++) 
	    players[i].iD = i;
    }


    // organise les joueurs et les systemnetworks
    void organisePlayers() {
	int[] tablePlayers = new int[4];
	Player[] p = new Player[4];
	ServerNetwork[] c = new ServerNetwork[4];
	tablePlayers[0] = teams[0].players[0].iD;
	tablePlayers[1] = teams[1].players[0].iD;
	tablePlayers[2] = teams[0].players[1].iD;
	tablePlayers[3] = teams[1].players[1].iD;
	for (int i=0; i<4; i++) {
	    p[i] = players[tablePlayers[i]];
	    c[i] = myServerNetwork[tablePlayers[i]];
	}
	for (int i=0; i<4; i++) {
	    players[i] = p[i];
	    myServerNetwork[i] = c[i];
	    myServerNetwork[i].setClientId(p[i].iD);
	    
	}
    }



    boolean calculateTeam(int[] choosenCards) {
	int lowest = 0;
	int highest = 0;
	boolean ok = true;
	for (int i=1; i<4; i++) {
	    if ((choosenCards[i] % 9) < (choosenCards[lowest] % 9))
		lowest = i;
	    if ((choosenCards[i] % 9) > (choosenCards[highest] % 9))
		highest = i;
	}
	for (int i=0; i<4; i++) {
	    if (i != lowest)
		if ((choosenCards[i] % 9) == (choosenCards[lowest] % 9))
		    ok = false;
	    if (i != highest)
		if ((choosenCards[i] % 9) == (choosenCards[highest] % 9))
		    ok = false;
	}
	if (ok) {
	    int j = 0;
	    for (int k=0; k<4; k++)
		if (k == lowest) {
		    teams[0].players[0] = players[lowest];
		    players[lowest].myTeam = 0;
		}
		else
		    if (k == highest) {
			teams[0].players[1] = players[highest];
			players[highest].myTeam = 0;
		    }
		    else {
			teams[1].players[j] = players[k];
			players[k].myTeam = 1;
			j++;
		    }

	}
	return ok;
    }


    // distribue les cartes
    int[] chooseCards() {
	final int[] cards = new int[36]; 
	boolean[] usedCards = new boolean[36];
	Random rand = new Random();
	for (int i=0; i<36; i++)
	    usedCards[i] = false;
	int j;
	for (int i=0; i<35; i++) {
	    do {
		j = (int)(rand.nextDouble() * 36);
	    } while (usedCards[j]);
	    cards[i] = j;
	    usedCards[j] = true;
	}
	j = 0;
	while (usedCards[j])
	    j++;
	cards[35] = j;
	return cards;
    }


    void playPart() throws ClientLeftException{
	/* randomly choose the cards and send them */
	firstToPlay = distribute();
	String answer;
	int nextPlayer;
	do {
	    chooseAtout();                     // choisit l'atout
	    nextPlayer = firstToPlay;

	    /* for (int i=0; i<9; i++)            // fait jouer les 9 plies
	       nextPlayer = playPlie(nextPlayer);          */

	    int j=0;
	    while ((j<9) && (nextPlayer != -1)) {   // fait jouer les 9 plies
		nextPlayer = playPlie(nextPlayer);
		j++;
	    }

	    if (nextPlayer != -1) {    // si personne n'a gagn� : on continue normalement
		// 5 de der
		if (atout == 0)
		    teams[players[currentPlie.owner].myTeam].addScore(10);
		else
		    teams[players[currentPlie.owner].myTeam].addScore(5);

		for (int i=0; i<4; i++) {   // envoie le score
		    myServerNetwork[i].sendStr("18 " + String.valueOf(teams[players[i].myTeam].getScore())
					       + " " + String.valueOf(teams[(players[i].myTeam + 1)%2].getScore()));
		    answer = myServerNetwork[i].rcvStr();  // r�ponse
		}

		/* waits a few seconds so that the players can see the last 
		 * cards and the score */
		try {
		    Thread.sleep(2000);
		} catch (InterruptedException e) {
		}

		firstToPlay = (firstToPlay + 1) % 4;
		distribute();
	    }
	    else { // si une �quipe a gagn�
		for (int i=0; i<4; i++) {   // envoie le score
		    myServerNetwork[i].sendStr("18 " + String.valueOf(teams[players[i].myTeam].getScore())
					       + " " + String.valueOf(teams[(players[i].myTeam + 1)%2].getScore()));
		    answer = myServerNetwork[i].rcvStr();  // r�ponse
		}
	    }

	    // r�p�te jusqu'� ce qu'on gagne
	} while ((!teams[0].won) && (!teams[1].won));       
    
	// Sends the winner to all player
	int winner = teams[0].won ? 0 : 1;
	for (int i=0; i<4; i++) {
	    myServerNetwork[i].sendStr("21 " + String.valueOf(winner) + " " +
				       String.valueOf(teams[winner].players[0].iD) + " " +
				       String.valueOf(teams[winner].players[1].iD));
	    answer = myServerNetwork[i].rcvStr();
	}

	/* waits a few seconds so that the players can see all the cards */
	try {
	    Thread.sleep(4000);
	} catch (InterruptedException e) {
	}

    }


    void chooseAtout() throws ClientLeftException {
	myServerNetwork[firstToPlay].sendStr("11");   // demande de faire atout en premier
	String[] instr = new String[10];
	instr = decode(myServerNetwork[firstToPlay].rcvStr());  // r�ponse
	Integer temp = Integer.valueOf(instr[1]);
	if (temp.intValue() == 4) {     // si on passe
	    int second = (firstToPlay + 2) % 4;
	    myServerNetwork[second].sendStr("12");   // demande de faire atout en second
	    instr = decode(myServerNetwork[second].rcvStr());  // r�ponse
	}
	temp = Integer.valueOf(instr[1]);
	atout = temp.intValue();
	for (int i=0; i<4; i++) {
	    // envoie l'atout choisi
	    myServerNetwork[i].sendStr("13 " + instr[1] + " " + firstToPlay); 
	    instr[2] = myServerNetwork[i].rcvStr();  // r�ponse
	}
    }



    int distribute() throws ClientLeftException {
	int seven = 0;      // 7 de carreau
	int[] cards = new int[36];
	cards = chooseCards();     // choisir les cartes au hasard
	String s;                  // cha�ne � envoyer
	for (int i=0; i<4; i++) {
	    s = "10";
	    for (int j=0; j<9; j++) {
		s +=  " " + String.valueOf(cards[i*9+j]);
		if (cards[i*9+j] == 19)    // 7 de carreau
		    seven = i;
	    }
	    myServerNetwork[i].sendStr(s);
	    s = myServerNetwork[i].rcvStr();
	}
	return seven;
    }


    int playPlie(int player) throws ClientLeftException {
	currentPlie.owner = player;
	currentPlie.coupe = 0;
	myServerNetwork[player].sendStr("14");    // demande de jouer en premier
	String[] instr = new String[10];
	instr = decode(myServerNetwork[player].rcvStr()); //r�ponse
	Integer temp = Integer.valueOf(instr[1]);
	currentPlie.color = temp.intValue() / 9;
	currentPlie.highest = temp.intValue() % 9;
	temp = Integer.valueOf(instr[2]);
	currentPlie.score = temp.intValue();
    
	temp = Integer.valueOf(instr[3]); // Annonces ?
	int[] instr2 = new int[10];		// tableau d'instructions en integer
	switch (temp.intValue()) {
	    case 1 : 	// Annonces
    		System.out.println("Annonces");
    		myServerNetwork[player].sendStr("19");  // demande des pr�cisions sur l'annonce 
    		instr2 = decodint(myServerNetwork[player].rcvStr()); //r�ponse
    		for (int i=0; i<instr2[1]; i++) 
		    players[player].addAnounce(instr2[2+ 2*i], instr2[3 + 2*i], instr2[0]);		
    		break;
	    case 2 :	// St�ck
    		System.out.println("Annonces");
    		players[player].addAnounce(0, 0, player);
    		break;
	    case 3 :	// St�ck + annonces
    		System.out.println("Annonces");
    		myServerNetwork[player].sendStr("19");  // demande des pr�cisions sur l'annonce 
    		instr2 = decodint(myServerNetwork[player].rcvStr()); //r�ponse
    		for (int i=0; i<instr2[1]; i++) 
		    players[player].addAnounce(instr2[2+ 2*i], instr2[3 + 2*i], instr2[0]);		
    		players[player].addAnounce(0, 0, player);   	
	}

	for (int i=0; i<3; i++) {           // envoie la carte jou�e aux autres
	    myServerNetwork[(player + i + 1) % 4].sendStr("15 " + String.valueOf(player)+" "+ instr[1]);
	    instr[9] =  myServerNetwork[(player + i + 1) % 4].rcvStr();      // r�ponse
	}

	int playedCard;
	for (int i=0; i<3; i++) {     // demande de jouer
	    myServerNetwork[(player + i + 1) % 4].sendStr("16 "+String.valueOf(currentPlie.highest)+" "+String.valueOf(currentPlie.color)+" "+String.valueOf(currentPlie.coupe));
	    instr = decode(myServerNetwork[(player + i + 1) % 4].rcvStr()); //r�ponse
	    temp = Integer.valueOf(instr[1]);
	    playedCard = temp.intValue();
      
	    temp = Integer.valueOf(instr[3]); // Annonces ?
	    switch (temp.intValue()) {
		case 1 : 	// Annonces
		    System.out.println("Annonces");
		    myServerNetwork[(player + i + 1) % 4].sendStr("19");  // demande des pr�cisions sur l'annonce 
		    instr2 = decodint(myServerNetwork[(player + i + 1) % 4].rcvStr()); //r�ponse
		    for (int j=0; j<instr2[1]; j++) 
    			players[(player + i + 1) % 4].addAnounce(instr2[2+ 2*j], instr2[3 + 2*j], instr2[0]);		
		    break;
		case 2 :	// St�ck
		    System.out.println("Annonces");
		    players[(player + i + 1) % 4].addAnounce(0, 0, (player + i + 1) % 4);
		    break;
		case 3 :	// St�ck + annonces
		    System.out.println("Annonces");
		    myServerNetwork[(player + i + 1) % 4].sendStr("19");  // demande des pr�cisions sur l'annonce 
		    instr2 = decodint(myServerNetwork[(player + i + 1) % 4].rcvStr()); //r�ponse
		    for (int j=0; j<instr2[1]; j++) 
    			players[(player + i + 1) % 4].addAnounce(instr2[2+ 2*j], instr2[3 + 2*j], instr2[0]);
		    /* stock */
		    players[(player + i + 1) % 4].addAnounce(0, 0, (player + i + 1) % 4);   	
	    }

	    for (int j=0; j<3; j++) {             // envoie la carte jou�e aux autres
		myServerNetwork[(player + j + i + 2) % 4].sendStr("15 " + String.valueOf((player + i + 1) % 4) + " " + instr[1]);
		instr[9] =  myServerNetwork[(player + j + i + 2) % 4].rcvStr();      // r�ponse
	    }
	    if ((playedCard / 9) == atout) {
		if (currentPlie.color != atout) { // si on coupe
		    if (currentPlie.coupe == 1) { // already cut
			switch (playedCard % 9) { // surcoupe
			    case 3:  // si on joue le nell
				if (currentPlie.highest != 5) {   
				    currentPlie.highest = playedCard % 9;
				    currentPlie.owner = (player + i + 1) % 4;
				}
				break;
			    case 5:  // si on joue le bourg
				currentPlie.highest = playedCard % 9;
				currentPlie.owner = (player + i + 1) % 4;
				break;
			    default: // sinon
				if (((currentPlie.highest!=5) && 
				     (currentPlie.highest!=3)) && 
				    ((playedCard % 9) > currentPlie.highest)) {
				    currentPlie.highest = playedCard % 9;
				    currentPlie.owner = (player + i + 1) % 4;
				}
			}
			// else souscoupe => nothing to do
		    }
		    else {  // first to cut
			currentPlie.coupe = 1;
			currentPlie.highest = playedCard % 9;
			currentPlie.owner = (player + i + 1) % 4;
		    }
		}
		else         // si c'est jou� atout
		    switch (playedCard % 9) {
			case 3 :  if (currentPlie.highest != 5) {   // si on joue le nell
			    currentPlie.highest = playedCard % 9;
			    currentPlie.owner = (player + i + 1) % 4;
			}
			break;
			case 5 :  // si on joue le bourg
			    currentPlie.highest = playedCard % 9;
			    currentPlie.owner = (player + i + 1) % 4;
			    break;
			default : // sinon
			    if (((currentPlie.highest!=5) && (currentPlie.highest!=3)) && ((playedCard % 9) > currentPlie.highest)) {
				currentPlie.highest = playedCard % 9;
				currentPlie.owner = (player + i + 1) % 4;
			    }
		    }
	    }
	    else  if ((playedCard / 9) == currentPlie.color)
		if (((playedCard % 9) > currentPlie.highest) && (currentPlie.coupe == 0)) {
		    currentPlie.owner = (player + i + 1) % 4;
		    currentPlie.highest = playedCard % 9;
		}
	    temp = Integer.valueOf(instr[2]);
	    currentPlie.score = currentPlie.score + temp.intValue(); // augmente le score de la plie
	}

	/* now everybody has played ... */

	/* waits a few seconds so that the players can see all the cards */
	try {
	    Thread.sleep(1500);
	} catch (InterruptedException e) {
	}

	// communique qui a pris la plie
	for (int i=0; i<4; i++) {       
	    myServerNetwork[i].sendStr("17 " + String.valueOf(currentPlie.owner));
	    instr[9] =  myServerNetwork[i].rcvStr();      // r�ponse
	}
    
	// choix et comptabilisation des annonces
	int stock = -1;
	int maxAnounce = 1;
	int maxHeight = 0;
	int anouncingTeam = -1;  // joueur qui a la plus grosse annonce
	for (int j=0; j<4; j++)
	    for (int i=0; i<players[j].nbrAnounces; i++) {
		System.out.println("player " + j + ", announce : " +
				   players[j].anounces[i].type + ", height : "
				   + Card.getHeight(players[j].anounces[i].card));
    		if (players[j].anounces[i].type > maxAnounce) {  
		    // plus grosse annonce
		    anouncingTeam = j;
		    maxAnounce = players[j].anounces[i].type;
		    maxHeight = Card.getHeight(players[j].anounces[i].card);
    		} 			
    		else if ((players[j].anounces[i].type == maxAnounce) &&
			 (Card.getHeight(players[j].anounces[i].card) > 
			  maxHeight)) { 
		    // m�me annonce plus haute
		    anouncingTeam = j;
		    maxHeight = Card.getHeight(players[j].anounces[i].card);
    		}
		else if ((players[j].anounces[i].type == maxAnounce) &&
			 (Card.getHeight(players[j].anounces[i].card) 
			  == maxHeight)) {
		    // meme annonce, meme hauteur
		} 
    		if (players[j].anounces[i].type == 0)
		    stock = j;
	    }
	String info;
	System.out.println("Bigger 'annonce' : "+ anouncingTeam);
    
	if (anouncingTeam != -1) { // there are announces
	    for (int i=0; i<4; i++) {
		if (((i == anouncingTeam) || (i == ((anouncingTeam + 2) % 4)))
		    && (players[i].nbrAnounces > 0)){
		    // annonceur

		    info = "20 " + i + " " + players[i].nbrAnounces;
		    for (int j=0; j<players[i].nbrAnounces; j++) {
			info = info + " " + players[i].anounces[j].type + " " 
			    + players[i].anounces[j].card;
			if (atout == 0) // pique
			    teams[players[i].myTeam].addScore(
				2*Card.anounceValue[players[i].anounces[j].
						   type]);
			else
			    teams[players[i].myTeam].addScore(
				Card.anounceValue[players[i].anounces[j].
						 type]);
		    }
		    for (int j=0; j<4; j++) {
			// communique les annonces
			myServerNetwork[j].sendStr(info);            
			instr = decode(myServerNetwork[j].rcvStr()); // r�ponse
		    } 	
		} else  if (i == stock) { /* THIS CASE SHOULDN'T HAPPEN because
					   * if we don't have any announce at
					   * first plie, there is no need to
					   * declare stock */
		    info = "20 " + i + " 1 0 0";
		    for (int j=0; j<4; j++) {
			// communique les annonces
			myServerNetwork[j].sendStr(info);
			instr = decode(myServerNetwork[j].rcvStr()); // r�ponse
		    } 
		    // add stock points
		    if (atout == 0) 
			teams[players[i].myTeam].addScore(
			    2*Card.anounceValue[0]);
		    else
			teams[players[i].myTeam].addScore(
			    Card.anounceValue[0]);
		}
		players[i].clearAnounces();
	    }
	}
	else if (stock != -1) { // no announce but stock
	    info = "20 " + stock + " 1 0 0";
	    for (int j=0; j<4; j++) {
		// communique les annonces
		myServerNetwork[j].sendStr(info);
		instr = decode(myServerNetwork[j].rcvStr()); // r�ponse
	    }
	    // add stock points
	    if (atout == 0) 
		teams[players[stock].myTeam].addScore(
		    2*Card.anounceValue[0]);
	    else
		teams[players[stock].myTeam].addScore(
		    Card.anounceValue[0]);
	    players[stock].clearAnounces();
	}
 
	// comptabilisation des points
	if (atout == 0)       // pique
	    teams[players[currentPlie.owner].myTeam].addScore(currentPlie.score * 2);
	else
	    teams[players[currentPlie.owner].myTeam].addScore(currentPlie.score);

	int returnValue = currentPlie.owner;;
	if ((teams[0].won) || (teams[1].won))
	    returnValue = -1;
	return returnValue;
    }


}



//**************** Autres Classes **********************************************


class Player {
    // Variables
    public String firstName;
    public String lastName;
    public int iD;		     // num�ro de 0 � 3
    public int myTeam;
    public int nbrAnounces;
    public Anounce[] anounces = new Anounce[4];	// annonces
	
    // Constructeur
    public Player() {
	nbrAnounces = 0;
	for (int i=0; i<4; i++)
	    anounces[i] = new Anounce();
    }
	
    // M�thodes
    public void clearAnounces() {
	nbrAnounces = 0;
    }
	
    public void addAnounce(int type, int card, int player) {
	anounces[nbrAnounces].type   = type;
	anounces[nbrAnounces].card   = card;
	anounces[nbrAnounces].player = player;
	nbrAnounces++;
    }
}

class Team {
    // Variables
    private int currentScore;
    public Player[] players;
    public boolean won;
  	
	// Constructeur
    public Team() {
	won = false;
	players = new Player[2];
	for (int i=0; i<2; i++)
	    players[i] = new Player();
	currentScore = 0;
    }
	
    // M�thodes
    public void resetScore() {
	currentScore = 0;
	won = false;
    }
	
    public void addScore(int score) {
	currentScore += score;
	if (currentScore > 1499)
	    won = true;
    }
	
    public int getScore() {
	return currentScore;
    }
}

class Plie {
    public int highest;   // la plus haute carte de la plie (celle qui tient la plie)
    public int color;     // la couleur demand�e
    public int score;     // la valeur de la plie
    public int coupe;     // 0 : pas coup�, 1 : coup�
    public int owner;     // iD de celui qui tient la plie
}

/* ************************** REMARQUE ******************************
 	Les cartes sont en fait repr�sent�es par des int de 0 � 35
 	o� "carte div 9" donne sa couleur et "carte mod 9" sa
 	hauteur. La classe Card est donc utilis�e ici que pour
 	les m�thodes statiques getColor et getHeight.
   *************************************************************** */
   
abstract class Card {
    static final int[] anounceValue = {20, 20, 50, 100, 100, 150, 200};
	
    public static int getColor(int card) {
	return card / 9;
    }
	
    public static int getHeight(int card) {
	return card % 9;
    }
}

class Anounce {
    int type;     // 0: st�ck, 1: 3 cartes, 2: cinquante, 3: cent, 4: carr�
    int card;   // plus haute carte de l'annonce
    int player;
}










