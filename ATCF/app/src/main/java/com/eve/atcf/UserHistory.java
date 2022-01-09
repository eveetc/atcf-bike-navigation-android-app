package com.eve.atcf;

import java.util.List;

public class UserHistory {
    String date;
    double lat;
    double lng;
    float azim;
    double distance;
    List<Node> allVisitedNodes;

    public UserHistory(String date, double lat, double lng, float azim, double distance, List<Node> allVisitedNodes) {
        this.date = date;
        this.lat = lat;
        this.lng = lng;
        this.azim = azim;
        this.distance = distance;
        this.allVisitedNodes = allVisitedNodes;
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

    protected List<Node> getAllVisitedNodes() {
        return this.allVisitedNodes;
    }
}
