package com.leflat.jass.client;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.MaskFormatter;
import java.awt.*;
import java.awt.event.*;
import java.text.ParseException;

public class DialogConnection extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JTextField textFieldName;
    private JTextField textFieldHost;
    private JCheckBox networkGameCheckBox;
    private JSpinner spinnerAiPlayers;
    private JFormattedTextField textFieldGameNumber;
    private JLabel labelGameId;
    private JLabel labelAiPlayer;
    private JCheckBox joinExistingGameCheckBox;
    private JLabel labelGameHost;
    private Border defaultBorder;

    public boolean ok;
    public String name;
    public String host;
    public int aiPlayers;
    public int gameId;
    public boolean local;

    public DialogConnection(Frame parent) {
        super(parent, true);
        setComponents();
    }

    public DialogConnection(Frame parent, String name, String host) {
        this(parent);
        textFieldName.setText(name);
        textFieldHost.setText(host);
    }

    private void setComponents() {
        setTitle("Connexion");
        setContentPane(contentPane);
        getRootPane().setDefaultButton(buttonOK);
        try {
            var formatter = new MaskFormatter("### ###");
            formatter.setPlaceholderCharacter('_');
            textFieldGameNumber.setFormatterFactory(new DefaultFormatterFactory(formatter));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        onNetworkCheckBox();
        onJoinCheckBox();
        spinnerAiPlayers.setModel(new SpinnerNumberModel(0, 0, 3, 1));
        defaultBorder = textFieldName.getBorder();

        buttonOK.addActionListener(e -> onOK());

        buttonCancel.addActionListener(e -> onCancel());

        joinExistingGameCheckBox.addActionListener(actionEvent -> onJoinCheckBox());

        networkGameCheckBox.addActionListener(actionEvent -> onNetworkCheckBox());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(e -> onCancel(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void onOK() {
        if (checkFieldErrors()) {
            return;
        }
        if (joinExistingGameCheckBox.isSelected()) {
            String gameIdString = textFieldGameNumber.getText();
            gameId = Integer.parseInt(gameIdString.replace(" ", ""));
            aiPlayers = 0;
        } else {
            aiPlayers = (Integer) spinnerAiPlayers.getValue();
            gameId = -1;
        }
        name = textFieldName.getText();
        host = textFieldHost.getText();
        local = !networkGameCheckBox.isSelected();
        ok = true;
        dispose();
    }

    private boolean checkFieldErrors() {
        boolean errors = false;
        if (textFieldHost.getText().isBlank()) {
            Border border = BorderFactory.createLineBorder(Color.RED, 2);
            textFieldHost.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 7, 2, 7)));
            errors = true;
        } else {
            textFieldHost.setBorder(defaultBorder);
        }
        if (textFieldName.getText().isBlank()) {
            Border border = BorderFactory.createLineBorder(Color.RED, 2);
            textFieldName.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 7, 2, 7)));
            errors = true;
        } else {
            textFieldName.setBorder(defaultBorder);
        }
        if (joinExistingGameCheckBox.isSelected() && networkGameCheckBox.isSelected()) {
            if (textFieldGameNumber.getValue() == null) {
                Border border = BorderFactory.createLineBorder(Color.RED, 2);
                textFieldGameNumber.setBorder(BorderFactory.createCompoundBorder(border, BorderFactory.createEmptyBorder(2, 7, 2, 7)));
                errors = true;
            } else {
                textFieldGameNumber.setBorder(defaultBorder);
            }
        } else {
            textFieldGameNumber.setBorder(defaultBorder);
        }
        return errors;
    }

    private void onCancel() {
        ok = false;
        dispose();
    }

    private void onJoinCheckBox() {
        if (!networkGameCheckBox.isSelected()) {
            return;
        }
        boolean joinGame = joinExistingGameCheckBox.isSelected();
        textFieldGameNumber.setEnabled(joinGame);
        labelGameId.setEnabled(joinGame);
        spinnerAiPlayers.setEnabled(!joinGame);
        labelAiPlayer.setEnabled(!joinGame);
    }

    private void onNetworkCheckBox() {
        boolean networkGame = networkGameCheckBox.isSelected();
        joinExistingGameCheckBox.setEnabled(networkGame);
        textFieldHost.setEnabled(networkGame);
        labelGameHost.setEnabled(networkGame);
        if (networkGame) {
            onJoinCheckBox();
        } else {
            textFieldGameNumber.setEnabled(false);
            labelGameId.setEnabled(false);
            spinnerAiPlayers.setEnabled(false);
            labelAiPlayer.setEnabled(false);
        }
    }


    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        contentPane = new JPanel();
        contentPane.setLayout(new GridLayoutManager(4, 2, new Insets(10, 10, 10, 10), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
        panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        buttonOK = new JButton();
        buttonOK.setText("OK");
        panel2.add(buttonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        buttonCancel = new JButton();
        buttonCancel.setText("Cancel");
        panel2.add(buttonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel3, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Player name");
        panel3.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textFieldName = new JTextField();
        panel3.add(textFieldName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
        contentPane.add(panel4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel4.add(spacer2, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        networkGameCheckBox = new JCheckBox();
        networkGameCheckBox.setText("Network game");
        panel4.add(networkGameCheckBox, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        labelGameId = new JLabel();
        labelGameId.setText("Game number");
        panel4.add(labelGameId, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textFieldGameNumber = new JFormattedTextField();
        panel4.add(textFieldGameNumber, new GridConstraints(2, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        labelAiPlayer = new JLabel();
        labelAiPlayer.setText("AI players");
        panel4.add(labelAiPlayer, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        spinnerAiPlayers = new JSpinner();
        panel4.add(spinnerAiPlayers, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        labelGameHost = new JLabel();
        labelGameHost.setText("Game host");
        panel4.add(labelGameHost, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        textFieldHost = new JTextField();
        textFieldHost.setText("localhost");
        panel4.add(textFieldHost, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        joinExistingGameCheckBox = new JCheckBox();
        joinExistingGameCheckBox.setText("Join existing game");
        panel4.add(joinExistingGameCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return contentPane;
    }

}