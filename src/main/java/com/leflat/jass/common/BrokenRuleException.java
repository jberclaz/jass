package com.leflat.jass.common;

public class BrokenRuleException extends Exception {
    int brokenRule;

    public BrokenRuleException(int brokenRule) {
        this.brokenRule = brokenRule;
    }

    public int getBrokenRule() { return brokenRule; }
}
