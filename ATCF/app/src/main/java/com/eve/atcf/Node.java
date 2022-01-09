package com.eve.atcf;


public class Node {
    int name;
    double lat;
    double lng;
    int indicator;

    public Node(int name, double lat, double lng, int indicator) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.indicator = indicator;
    }

    private int getName() {
        return this.name;
    }

    protected Double getLat() {
        return this.lat;
    }

    protected Double getLng() {
        return this.lng;
    }

    protected int getIndicator() {
        return this.indicator;
    }
}
