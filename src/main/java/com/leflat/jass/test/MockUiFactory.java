package com.leflat.jass.test;

import com.leflat.jass.common.IJassUi;
import com.leflat.jass.common.IJassUiFactory;
import com.leflat.jass.common.IConnectable;

public class MockUiFactory implements IJassUiFactory {
    private float delaySeconds = 0;
    private int nbrGames = 1;

    public MockUiFactory(float delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public MockUiFactory(float delaySeconds, int nbrGames) {
        this(delaySeconds);
        this.nbrGames = nbrGames;
    }

    public MockUiFactory() {}

    @Override
    public IJassUi getUi(IConnectable remotePlayer) {
        return new MockUi(remotePlayer, delaySeconds, nbrGames);
    }
}
