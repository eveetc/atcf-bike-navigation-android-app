package com.eve.atcfplus;

import android.location.Location;

public class IndicatedStreet {
    protected Location getTriggerStreet() {
        return this.triggerStreet;
    }

    protected Location getIndicatedStreet() {
        return this.indicatedStreet;
    }

    protected int getIndicator() {
        return this.indicator;
    }

    Location triggerStreet;
    Location indicatedStreet;
    int indicator;

    public IndicatedStreet(Location triggerStreet, Location indicatedStreet, int indicator) {
        this.triggerStreet = triggerStreet;
        this.indicatedStreet = indicatedStreet;
        this.indicator = indicator;
    }
}
