package com.leflat.jass.test;

import com.leflat.jass.client.ServerDisconnectedException;
import com.leflat.jass.common.ClientConnectionInfo;
import com.leflat.jass.common.ConnectionError;
import com.leflat.jass.common.IClientNetwork;
import com.leflat.jass.common.IClientNetworkFactory;
import com.leflat.jass.client.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class ScriptedClientNetwork implements IClientNetwork {
    private ArrayList<String> script;
    private int index = 0;

    public ScriptedClientNetwork(String script) {
        assert(script != null);
        this.script = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(script))) {
            String line;
            while ((line = br.readLine()) != null) {
                this.script.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ClientConnectionInfo connect(String host, int requestedGameId, String name) {
        return new ClientConnectionInfo(0, 388230, ConnectionError.CONNECTION_SUCCESSFUL);
    }

    @Override
    public boolean isConnected() {
        return index < script.size();
    }

    @Override
    public void disconnect() {

    }

    @Override
    public void sendMessage(List<String> message) {

    }

    @Override
    public String receiveRawMessage() throws ServerDisconnectedException {
        if (index < script.size()) {
            return script.get(index++);
        }
        throw new ServerDisconnectedException("End of script");
    }
}

class ScriptedClientNetworkFactory implements IClientNetworkFactory {
    private String scriptPath;
    public ScriptedClientNetworkFactory(String scriptPath) {
        this.scriptPath = scriptPath;
    }

    @Override
    public IClientNetwork getClientNetwork() {
        return new ScriptedClientNetwork(scriptPath);
    }
}

public class AutomaticClientTesting {
    public static void main(String[] aregs) {
        //var filePath = AutomaticClientTesting.class.getClassLoader().getResource("test/messages_entire_game.txt");
        var filePath = "/home/jrb/src/external/jass/target/classes/test/messages_entire_game.txt";
        var scriptedClientNetworkfactory = new ScriptedClientNetworkFactory(filePath);

        var player = new JassPlayer(scriptedClientNetworkfactory, new ModernUiFactory());
    }
}
