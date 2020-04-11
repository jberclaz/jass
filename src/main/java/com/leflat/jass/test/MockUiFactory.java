package com.leflat.jass.test;

import com.leflat.jass.common.IJassUi;
import com.leflat.jass.common.IJassUiFactory;
import com.leflat.jass.common.IRemotePlayer;

public class MockUiFactory implements IJassUiFactory {
    private float delaySeconds = 0;

    public MockUiFactory(float delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public MockUiFactory() {}

    @Override
    public IJassUi getUi(IRemotePlayer remotePlayer) {
        return new MockUi(remotePlayer, delaySeconds);
    }
}
