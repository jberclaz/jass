package com.leflat.jass.common;

import java.util.concurrent.locks.Lock;

public interface IController extends Runnable {
    Lock getLock();

    void terminate();
}
