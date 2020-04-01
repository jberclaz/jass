package com.leflat.jass.client;

import java.awt.*;

public class JassCanvas extends Canvas {
    protected int mode;   // 0 : rien, 1 : jouer
    protected String name;
    protected boolean atout;

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setAtout(boolean atout) {
        this.atout = atout;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMode() {
        return mode;
    }
}
