/*
 * DialogPartnerChoice.java
 *
 * Created on 19. avril 2000, 11:44
 */

/**
 *
 * @author  Berclaz Jérôme
 * @version
 */

package com.leflat.jass.client;


public class DialogPartnerChoice extends javax.swing.JDialog {
  int number = 0;

  /** Creates new form DialogPartnerChoice */
  public DialogPartnerChoice(java.awt.Frame parent,boolean modal) {
    super (parent, modal);
    initComponents ();
    pack ();
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the FormEditor.
   */
  private void initComponents () {//GEN-BEGIN:initComponents
    jComboBoxPartner = new javax.swing.JComboBox ();
    jButtonOk = new javax.swing.JButton ();
    jLabel1 = new javax.swing.JLabel ();
    getContentPane ().setLayout (new AbsoluteLayout ());
    setSize(300, 200);
    setResizable (false);
    setTitle ("Choix du partenaire");
    addWindowListener (new java.awt.event.WindowAdapter () {
      public void windowClosing (java.awt.event.WindowEvent evt) {
        closeDialog (evt);
      }
    }
    );



    getContentPane ().add (jComboBoxPartner, new AbsoluteConstraints (100, 50, -1, -1));

    jButtonOk.setText ("Ok");
    jButtonOk.addActionListener (new java.awt.event.ActionListener () {
      public void actionPerformed (java.awt.event.ActionEvent evt) {
        jButtonOkActionPerformed (evt);
      }
    }
    );


    getContentPane ().add (jButtonOk, new AbsoluteConstraints (120, 120, -1, -1));

    jLabel1.setText ("Veuillez choisir votre partenaire");


    getContentPane ().add (jLabel1, new AbsoluteConstraints (20, 20, -1, -1));

  }//GEN-END:initComponents

  private void jButtonOkActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOkActionPerformed
// Add your handling code here:
    number = jComboBoxPartner.getSelectedIndex();
    this.dispose();
  }//GEN-LAST:event_jButtonOkActionPerformed

  /** Closes the dialog */
  private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
    setVisible (false);
    dispose ();
  }//GEN-LAST:event_closeDialog




  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JComboBox jComboBoxPartner;
  private javax.swing.JButton jButtonOk;
  private javax.swing.JLabel jLabel1;
  // End of variables declaration//GEN-END:variables

  public void addPlayer(String name) {
    jComboBoxPartner.addItem(name);
  }
}
