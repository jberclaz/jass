package com.leflat.jass.client;

import com.leflat.jass.common.IJassUi;
import com.leflat.jass.common.IJassUiFactory;
import com.leflat.jass.common.IRemotePlayer;

public class OriginalUiFactory implements IJassUiFactory {
    @Override
    public IJassUi getUi(IRemotePlayer remotePlayer) {
        return new JassFrame(remotePlayer);
    }
}
