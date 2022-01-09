package com.eve.atcfplus;

public class UserHistoryActivatedQuadrant {
    float azim;
    String visibility;

    public UserHistoryActivatedQuadrant(float azim, String visibility) {
        this.azim = azim;
        this.visibility = visibility;
    }

    protected float getAzim() {
        return this.azim;
    }

    protected String getVisibility() {
        return visibility;
    }
}

