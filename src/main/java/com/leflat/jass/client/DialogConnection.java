package com.leflat.jass.client;

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
    private JCheckBox joinExistingGameCheckBox;
    private JSpinner spinnerAiPlayers;
    private JFormattedTextField textFieldGameNumber;
    private JLabel labelGameId;
    private JLabel labelAiPlayer;
    private Border defaultBorder;

    public boolean ok;
    public String name;
    public String host;
    public int aiPlayers;
    public int gameId;

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
        onCheckBox();
        spinnerAiPlayers.setModel(new SpinnerNumberModel(0, 0, 3, 1));
        defaultBorder = textFieldName.getBorder();

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        joinExistingGameCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                onCheckBox();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
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
            aiPlayers = (Integer)spinnerAiPlayers.getValue();
            gameId = -1;
        }
        name = textFieldName.getText();
        host = textFieldHost.getText();
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
        if (joinExistingGameCheckBox.isSelected()) {
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

    private void onCheckBox() {
        boolean joinGame = joinExistingGameCheckBox.isSelected();
        textFieldGameNumber.setEnabled(joinGame);
        labelGameId.setEnabled(joinGame);
        spinnerAiPlayers.setEnabled(!joinGame);
        labelAiPlayer.setEnabled(!joinGame);
    }

      public static void main(String[] args) {
        var dialog = new DialogConnection(null, "name", "host");
        dialog.pack();
        dialog.setVisible(true);
        System.exit(0);
    }
}