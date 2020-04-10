package com.leflat.jass.client;

import com.leflat.jass.common.IClientNetwork;
import com.leflat.jass.common.IClientNetworkFactory;

public class ClientNetworkFactory implements IClientNetworkFactory {

    @Override
    public IClientNetwork getClientNetwork() {
        return new ClientNetwork();
    }
}
