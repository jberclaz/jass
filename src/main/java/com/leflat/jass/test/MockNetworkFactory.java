package com.leflat.jass.test;

import com.leflat.jass.common.IClientNetwork;
import com.leflat.jass.common.IClientNetworkFactory;

public class MockNetworkFactory implements IClientNetworkFactory {
    private int nbrPlayers = 0;

    public MockNetworkFactory() {

    }

    public MockNetworkFactory(int startId) {
        nbrPlayers = startId;
    }

    @Override
    public IClientNetwork getClientNetwork() {
        return new MockClientNetwork(nbrPlayers++);
    }
}
