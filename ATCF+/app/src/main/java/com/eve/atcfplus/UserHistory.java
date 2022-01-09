package com.eve.atcfplus;

import java.util.List;

public class UserHistory {
    String date;
    double lat;
    double lng;
    float azim;
    double distance;
    int indicator;
    UserHistoryActivatedQuadrant[] activatedQuadrants;
    List<Node> allVisitedNodes;
    String backgroundColor;

    public UserHistory(String date, double lat, double lng, float azim, double distance, int indicator, UserHistoryActivatedQuadrant[] activatedQuadrants, List<Node> allVisitedNodes, String backgroundColor) {
        this.date = date;
        this.lat = lat;
        this.lng = lng;
        this.azim = azim;
        this.distance = distance;
        this.indicator = indicator;
        this.activatedQuadrants = activatedQuadrants;
        this.allVisitedNodes = allVisitedNodes;
        this.backgroundColor = backgroundColor;
    }

    protected String getDate() {
        return this.date;
    }

    protected Double getLat() {
        return this.lat;
    }

    protected Double getLng() {
        return this.lng;
    }

    protected float getAzim() {
        return this.azim;
    }

    protected Double getDistance() {
        return this.distance;
    }

    protected int getIndicator() {
        return this.indicator;
    }

    protected UserHistoryActivatedQuadrant[] getActivatedQuadrants() {
        return this.activatedQuadrants;
    }

    protected List<Node> getAllVisitedNodes() {
        return this.allVisitedNodes;
    }
    protected  String getBackgroundColor(){
        return this.backgroundColor;
    }
}
