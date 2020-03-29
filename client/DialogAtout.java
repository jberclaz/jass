/*
 * DialogAtout.java
 *
 * Created on 19. avril 2000, 11:52
 */
 


/** 
 *
 * @author  Berclaz J�r�me
 * @version 
 */
public class DialogAtout extends javax.swing.JDialog {
  int number = 0;

  /** Creates new form DialogAtout */
  public DialogAtout(java.awt.Frame parent,boolean modal, boolean pass) {
    super (parent, modal);
    initComponents ();
    pack ();
    if (pass)
      jButtonPass.setEnabled(false);
    jComboBoxAtout.addItem("Pique");
    jComboBoxAtout.addItem("Coeur");
    jComboBoxAtout.addItem("Carreau");
    jComboBoxAtout.addItem("Tr�fle");
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the FormEditor.
   */
  private void initComponents () {//GEN-BEGIN:initComponents
    jLabel1 = new javax.swing.JLabel ();
    jComboBoxAtout = new javax.swing.JComboBox ();
    jButtonOk = new javax.swing.JButton ();
    jButtonPass = new javax.swing.JButton ();
    getContentPane ().setLayout (new AbsoluteLayout ());
    setSize(300, 180);
    setResizable (false);
    setTitle ("Choix de l'atout");
    addWindowListener (new java.awt.event.WindowAdapter () {
      public void windowClosing (java.awt.event.WindowEvent evt) {
        closeDialog (evt);
      }
    }
    );

    jLabel1.setText ("Veuillez choisir l'atout");


    getContentPane ().add (jLabel1, new AbsoluteConstraints (20, 20, -1, -1));



    getContentPane ().add (jComboBoxAtout, new AbsoluteConstraints (120, 60, -1, -1));

    jButtonOk.setText ("Ok");
    jButtonOk.addActionListener (new java.awt.event.ActionListener () {
      public void actionPerformed (java.awt.event.ActionEvent evt) {
        jButtonOkActionPerformed (evt);
      }
    }
    );


    getContentPane ().add (jButtonOk, new AbsoluteConstraints (40, 110, -1, -1));

    jButtonPass.setText ("Passer");
    jButtonPass.addActionListener (new java.awt.event.ActionListener () {
      public void actionPerformed (java.awt.event.ActionEvent evt) {
        jButtonPassActionPerformed (evt);
      }
    }
    );


    getContentPane ().add (jButtonPass, new AbsoluteConstraints (180, 110, -1, -1));

  }//GEN-END:initComponents

  private void jButtonPassActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonPassActionPerformed
// Add your handling code here:
    number = 4;
    this.dispose();
  }//GEN-LAST:event_jButtonPassActionPerformed

  private void jButtonOkActionPerformed (java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOkActionPerformed
// Add your handling code here:
    number = jComboBoxAtout.getSelectedIndex();
    this.dispose();
  }//GEN-LAST:event_jButtonOkActionPerformed

  /** Closes the dialog */
  private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
    setVisible (false);
    dispose ();
  }//GEN-LAST:event_closeDialog

  

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel jLabel1;
  private javax.swing.JComboBox jComboBoxAtout;
  private javax.swing.JButton jButtonOk;
  private javax.swing.JButton jButtonPass;
  // End of variables declaration//GEN-END:variables

}
