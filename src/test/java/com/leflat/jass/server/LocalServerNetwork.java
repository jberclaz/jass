package com.leflat.jass.server;

import com.leflat.jass.server.ServerNetwork;

import java.io.*;

public class LocalServerNetwork extends ServerNetwork {
    public LocalServerNetwork(PipedInputStream input, PipedOutputStream output, int playerId) {
        is = new BufferedReader(new InputStreamReader(input));
        os = new PrintWriter(output, true);
        this.playerId = playerId;
    }
}
