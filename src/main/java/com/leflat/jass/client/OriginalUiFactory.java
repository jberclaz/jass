package com.leflat.jass.client;

import com.leflat.jass.common.IConnectable;
import com.leflat.jass.common.IJassUi;
import com.leflat.jass.common.IJassUiFactory;

public class OriginalUiFactory implements IJassUiFactory {
    @Override
    public IJassUi getUi(IConnectable remotePlayer) {
        return new OriginalUi(remotePlayer);
    }
}
