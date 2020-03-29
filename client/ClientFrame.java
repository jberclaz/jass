/*
 * ClientFrame.java
 *
 * Created on 18. avril 2000, 16:10
 */



/**
 *
 * @author  Berclaz Jérôme
 * @version
 */
import java.awt.event.*;

public class ClientFrame extends javax.swing.JFrame {
    // Variables visuelles
    CanvasBorder leftCanvas;
    CanvasBorder rightCanvas;
    CanvasPlayer playerCanvas;
    CanvasTop topCanvas;
    CanvasCenter centerCanvas;
    CanvasLastPlie lastPlieCanvas;

    // Autres variables
    String path = "pics/";   // path where to find pictures
    FlatJassClientSystem app;

  /** Creates new form ClientFrame */
    public ClientFrame(FlatJassClientSystem app) {
	initComponents ();
	this.app = app;
	leftCanvas = new CanvasBorder(path);
	rightCanvas = new CanvasBorder(path);
	playerCanvas = new CanvasPlayer(path);
	topCanvas = new CanvasTop(path);
	centerCanvas = new CanvasCenter(path);
	lastPlieCanvas = new CanvasLastPlie(path);
	leftPanel.add(leftCanvas);
	rightPanel.add(rightCanvas);
	topPanel.add(topCanvas);
	playerPanel.add(playerCanvas);
	centerPanel.add(centerCanvas);
	lastPliePanel.add(lastPlieCanvas);
	playerCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
		    playerCanvas_mouseClicked(e);
		}
	    });
	centerCanvas.addMouseListener(new java.awt.event.MouseAdapter() {
		public void mouseClicked(MouseEvent e) {
		    centerCanvas_mouseClicked(e);
		}
	    });

	pack ();
    }

    /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the FormEditor.
   */
    private void initComponents () {//GEN-BEGIN:initComponents
	rightPanel = new javax.swing.JPanel ();
	leftPanel = new javax.swing.JPanel ();
	playerPanel = new javax.swing.JPanel ();
	topPanel = new javax.swing.JPanel ();
	centerPanel = new javax.swing.JPanel ();
	lastPliePanel = new javax.swing.JPanel ();
	jPanel1 = new javax.swing.JPanel ();
	statusBar = new javax.swing.JLabel ();
	infoBar = new javax.swing.JLabel ();
	jButtonAnounce = new javax.swing.JButton ();
	jButtonConnect = new javax.swing.JButton ();
	getContentPane ().setLayout (new AbsoluteLayout ());
	setSize(660, 565);
	setResizable (false);
	setTitle ("Flat Jass System");
	setFont (new java.awt.Font ("SansSerif", 0, 10));
	addWindowListener (new java.awt.event.WindowAdapter () {
		public void windowClosing (java.awt.event.WindowEvent evt) {
		    exitForm (evt);
		}
	    }
	    );

	rightPanel.setLayout (new java.awt.GridLayout (1, 1));
	rightPanel.setName ("rightPanel");


	getContentPane ().add (rightPanel, new AbsoluteConstraints (510, 0, 120,
								    450));

	leftPanel.setLayout (new java.awt.GridLayout (1, 1));
	leftPanel.setName ("leftPanel");


	getContentPane ().add (leftPanel, new AbsoluteConstraints (0, 0, 120,
								   450));

	playerPanel.setLayout (new java.awt.GridLayout (1, 1));


	getContentPane ().add (playerPanel, new AbsoluteConstraints (120, 330,
								     390, 120));

	topPanel.setLayout (new java.awt.GridLayout (1, 1));


	getContentPane ().add (topPanel, new AbsoluteConstraints (120, 0, 390,
								  120));

	centerPanel.setLayout (new java.awt.GridLayout (1, 1));
	centerPanel.setBackground (new java.awt.Color (51, 102, 0));


	getContentPane ().add (centerPanel, new AbsoluteConstraints (120, 120,
								     390, 210));

	lastPliePanel.setLayout (new java.awt.GridLayout (1, 1));


	getContentPane ().add (lastPliePanel, new AbsoluteConstraints (0, 450,
								       630, 40));

	jPanel1.setBorder (new javax.swing.border.BevelBorder(1));


	jPanel1.add (statusBar);
	jPanel1.add (infoBar);


	getContentPane ().add (jPanel1, new AbsoluteConstraints (0, 490, 400, 40));

	jButtonAnounce.setAlignmentY (0.4F);
	jButtonAnounce.setText ("Annoncer");
	jButtonAnounce.setEnabled(false);
	jButtonAnounce.addActionListener (new java.awt.event.ActionListener () {
		public void actionPerformed (java.awt.event.ActionEvent evt) {
		    jButtonAnounceActionPerformed (evt);
		}
	    }
	    );


	getContentPane ().add (jButtonAnounce, new AbsoluteConstraints (530, 500,
									-1, -1));

	jButtonConnect.setText ("Connexion");
	jButtonConnect.addActionListener (new java.awt.event.ActionListener () {
		public void actionPerformed (java.awt.event.ActionEvent evt) {
		    jButtonConnectActionPerformed (evt);
		}
	    }
	    );


	getContentPane ().add (jButtonConnect, new AbsoluteConstraints (410, 500,
									-1, -1));

    }//GEN-END:initComponents

    private void jButtonConnectActionPerformed (java.awt.event.ActionEvent evt)
    {
	//GEN-FIRST:event_jButtonConnectActionPerformed
	// Add your handling code here:
	if (jButtonConnect.getText() == "Connexion") {
	    DialogConnect dc = new DialogConnect(this, true);
	    dc.show();
	    if (dc.ok) {      // Connexion
		if (app.connect(dc.firstName, dc.lastName, dc.iP) != -1)
		    jButtonConnect.setText("Déconnexion");
	    }
	}
	else {
	    // TODO: DISPLAY A CONFIRMATION MSG
	    disconnect();
	    app.disconnect();
	}
    }//GEN-LAST:event_jButtonConnectActionPerformed

    public void disconnect() {
	for (int i=0; i<9; i++)
	    playerCanvas.hand[i] = 37; // effacer les cartes
	playerCanvas.setName("");
	playerCanvas.setAtout(false);
	leftCanvas.setNbrCards(0);
	leftCanvas.setMode(1);
	leftCanvas.setName("");
	leftCanvas.setAtout(false);
	rightCanvas.setNbrCards(0);
	rightCanvas.setMode(1);
	rightCanvas.setName("");
	rightCanvas.setAtout(false);
	topCanvas.setNbrCards(0);
	topCanvas.setMode(1);
	topCanvas.setName("");
	topCanvas.setAtout(false);
	centerCanvas.mode = 0;
	centerCanvas.repaint();
	removeLastPlie();
	lastPlieCanvas.atout = 4;
	setScore(0,0);
	repaint(31);

	statusBar.setText("Deconnexion");
	jButtonConnect.setText("Connexion");
    }

    private void jButtonAnounceActionPerformed (java.awt.event.ActionEvent evt) {
//GEN-FIRST:event_jButtonAnounceActionPerformed
// Add your handling code here:
	app.findAnounce();
    }//GEN-LAST:event_jButtonAnounceActionPerformed

    /** Exit the Application */
    private void exitForm(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_exitForm
	System.exit (0);
    }//GEN-LAST:event_exitForm



    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel rightPanel;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JPanel playerPanel;
    private javax.swing.JPanel topPanel;
    private javax.swing.JPanel centerPanel;
    private javax.swing.JPanel lastPliePanel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel statusBar;
    private javax.swing.JLabel infoBar;
    private javax.swing.JButton jButtonAnounce;
    private javax.swing.JButton jButtonConnect;
    // End of variables declaration//GEN-END:variables


  //Fait un repaint des différents canvas
    public void repaint(int nbr) {
	if ((nbr & 1) == 1)
	    playerCanvas.repaint();
	if ((nbr & 2) == 2)
	    leftCanvas.repaint();
	if ((nbr & 4) == 4)
	    topCanvas.repaint();
	if ((nbr & 8) == 8)
	    rightCanvas.repaint();
	if ((nbr & 16) == 16)
	    centerCanvas.repaint();
    }

    // attribue ses cartes aux joueur
    public void setPlayerCards(int[] hand) {
	playerCanvas.hand = hand;
    }

    // indique le nombre de cartes des adversaires / partenaire
    public void setOpponentCards(int player, int number) {
	int diff = player - app.myPlayer;
	if (diff < 0)
	    diff += 4;
	switch (diff) {
	    case 1 :  rightCanvas.setMode(1);
                rightCanvas.setNbrCards(number);
                break;
	    case 2 :  topCanvas.setMode(1);
                topCanvas.setNbrCards(number);
                break;
	    case 3 :  leftCanvas.setMode(1);
                leftCanvas.setNbrCards(number);
	}
    }

    void playerCanvas_mouseClicked(MouseEvent e) {
	if (playerCanvas.getMode() != 0)
	    if (((e.getY() > 19)&&(e.getY() < 116)) && ((e.getX() > 19)&&(e.getX()
									  < 371))) {
		System.out.println("Click");      // supprimer
		int x = e.getX();
		int i = 9;
		boolean found = false;
		while (!found && (i > 0)) {
		    i--;
		    if ((x > (19 + i * 35)) && (x < (91 + i * 35)))
			if (app.cards[i] < 36)
			    switch (app.playCard(playerCanvas.getMode(), app.cards[i])) {
				case 0 :  // jouer la carte
				    int playedCard = app.cards[i];
				    jButtonAnounce.setEnabled(false);
				    setStatusBar(""); // remove "a vous de..."
				    centerCanvas.cardsChoosen[0] = playedCard;
				    playerCanvas.setMode(0);
				    playerCanvas.hand[i] = 37;
				    app.cards[i] = 37;
				    found = true;
				    repaint(17);

				    // sends the card to the server
				    if (app.atout == Card.getColor(playedCard))
					app.sendCard(playedCard, Card.valueAtout[Card.getHeight(playedCard)]);        // modifier !!!!
				    else
					app.sendCard(playedCard, Card.value[Card.getHeight(playedCard)]);
				    break;
				case -1 : // suivre
				    System.out.println("Il faut suivre !!");
				    found = true;
				    break;
				case -2 : // sous-couper
				    System.out.println("Vous ne pouvez pas sous-couper!!");
				    found = true;
			    }
		}
	    }
    }

    public void setName(int player, String name) {
	int diff = player - app.myPlayer;
	if (diff < 0)
	    diff += 4;
	switch (diff) {
	    case 0 :  playerCanvas.setName(name);
		playerCanvas.repaint();
		break;
	    case 1 :  rightCanvas.setName(name);
		rightCanvas.repaint();
		break;
	    case 2 :  topCanvas.setName(name);
		topCanvas.repaint();
		break;
	    case 3 :  leftCanvas.setName(name);
		leftCanvas.repaint();
	}
    }

    public void teamChoiceShowCard(int player, int position, int cardNumber) {
	int diff = player - app.myPlayer;
	if (diff < 0)
	    diff += 4;
	switch (diff) {
	    case 0 :  for (int i=1; i<9; i++)
		playerCanvas.hand[i] = 37;
	    playerCanvas.hand[0] = cardNumber;
	    playerCanvas.repaint();
	    break;
	    case 1 :  centerCanvas.cardsChoosen[position] = app.myPlayer;
                centerCanvas.repaint();
                rightCanvas.setCard(playerCanvas.cards[cardNumber]);
                rightCanvas.setMode(0);      // tirage des équipes
                rightCanvas.repaint();
                break;
	    case 2 :  centerCanvas.cardsChoosen[position] = app.myPlayer;
                centerCanvas.repaint();
                topCanvas.setCard(playerCanvas.cards[cardNumber]);
                topCanvas.setMode(0);      // tirage des équipes
                topCanvas.repaint();
                break;
	    case 3 :  centerCanvas.cardsChoosen[position] = app.myPlayer;
                centerCanvas.repaint();
                leftCanvas.setCard(playerCanvas.cards[cardNumber]);
                leftCanvas.setMode(0);      // tirage des équipes
                leftCanvas.repaint();
	}
    }

    public void prepareTeamChoice() {
	for (int i=0; i<36; i++)
	    centerCanvas.cardsChoosen[i] = 10;   // carte présente
	for (int i=0; i<9; i++)
	    playerCanvas.hand[i] = 37; // effacer les cartes
	leftCanvas.setNbrCards(0);
	leftCanvas.setMode(1);
	rightCanvas.setNbrCards(0);
	rightCanvas.setMode(1);
	topCanvas.setNbrCards(0);
	topCanvas.setMode(1);
	centerCanvas.mode = 1;
	centerCanvas.repaint();
	removeLastPlie();
	lastPlieCanvas.atout = 4;
	setScore(0,0);
	repaint(31);
    }

    void centerCanvas_mouseClicked(MouseEvent e) {
	System.out.println("Click 1");
	if (centerCanvas.mode == 2) {        // à ton tour de tirer une carte
	    System.out.println("Click Center");
	    if (((e.getY() < 137) && (e.getY() > 39)) && ((e.getX() > 9) && (e.getX() < 361))) {
		int nbr = (e.getX() - 10) / 8;
		if (nbr > 35) {
                    if (centerCanvas.cardsChoosen[35] == 10)
			nbr = 35;
                    else  if ((centerCanvas.cardsChoosen[34] == 10) && (e.getX() < 353))
			nbr = 34;
		    else  if ((centerCanvas.cardsChoosen[33] == 10) && (e.getX() < 345))
			nbr = 33;
		    else  if ((centerCanvas.cardsChoosen[32] == 10) && (e.getX() < 337))
			nbr = 32;
		}

		if (centerCanvas.cardsChoosen[nbr] == 10) {
                    centerCanvas.cardsChoosen[nbr] = app.myPlayer;
                    centerCanvas.repaint();
		}
		else  if (centerCanvas.cardsChoosen[nbr-1] == 10) {
		    nbr--;
		    centerCanvas.cardsChoosen[nbr] = app.myPlayer;
		    centerCanvas.repaint();
		}
		else  if (centerCanvas.cardsChoosen[nbr-2] == 10) {
		    nbr-=2;
		    centerCanvas.cardsChoosen[nbr] = app.myPlayer;
		    centerCanvas.repaint();
		}
		else  if (centerCanvas.cardsChoosen[nbr-3] == 10) {
		    nbr-=3;
		    centerCanvas.cardsChoosen[nbr] = app.myPlayer;
		    centerCanvas.repaint();
		}
		centerCanvas.mode = 1;    // on ne peut plus choisir une carte
		setStatusBar("");         // remove "veuillez tirer..."
		app.sendCard(nbr, 0);
	    }
	}
    }

    // prépare l'écran pour une nouvelle partie
    void prepareMatch() {
	for (int i=0; i<4; i++)
	    centerCanvas.cardsChoosen[i] = 36;
	centerCanvas.mode = 3;      // mode de jeu
	lastPlieCanvas.atout = 4;
	lastPlieCanvas.repaint();
	repaint(31);          // repaint les 5 canvas
	setStatusBar("");
	removeLastPlie();
    }

    // affiche une carte jouée
    void showPlayedCard(int player, int number) {
	int diff = player - app.myPlayer;
	if (diff < 0)
	    diff += 4;
	switch (diff) {
	    case 1 :  centerCanvas.cardsChoosen[1] = number;
                rightCanvas.removeCard();
                rightCanvas.repaint();
                break;
	    case 2 :  centerCanvas.cardsChoosen[2] = number;
                topCanvas.removeCard();
                topCanvas.repaint();
                break;
	    case 3 :  centerCanvas.cardsChoosen[3] = number;
                leftCanvas.removeCard();
                leftCanvas.repaint();
	}
	centerCanvas.repaint();
	statusBar.setText(app.players[player].getFirstName() + " a joué...");
    }

    // ramasse la plie
    void pickUpPlie(String player) {
	statusBar.setText(player + " a pris la plie");
	for (int i=0; i<4; i++) {
	    System.out.println(i);
	    lastPlieCanvas.cards[i] = playerCanvas.cards[centerCanvas.cardsChoosen[i]];
	    centerCanvas.cardsChoosen[i] = 36;
	}
	lastPlieCanvas.display = true;
	lastPlieCanvas.repaint();
	centerCanvas.repaint();
    }

    void removeLastPlie() {
	lastPlieCanvas.display = false;
	lastPlieCanvas.repaint();
    }

    void setScore(int ourScore, int theirScore) {
	lastPlieCanvas.ourScore = ourScore;
	lastPlieCanvas.theirScore = theirScore;
	lastPlieCanvas.repaint();
    }


    void setStatusBar(String text) {
	statusBar.setText(text);
    }

    void setAnounceEnabled(boolean b) {
	jButtonAnounce.setEnabled(b);
    }

    void setAtout(int player) {
	int diff = player - app.myPlayer;
	if (diff < 0)
	    diff += 4;

	playerCanvas.setAtout(false);
	rightCanvas.setAtout(false);
	topCanvas.setAtout(false);
	leftCanvas.setAtout(false);

	switch (diff) {
	    case 0 :
		playerCanvas.setAtout(true);
		break;
	    case 1 :
                rightCanvas.setAtout(true);
                break;
	    case 2 :
                topCanvas.setAtout(true);
                break;
	    case 3 :
                leftCanvas.setAtout(true);
	}
	repaint(31);
    }
}
