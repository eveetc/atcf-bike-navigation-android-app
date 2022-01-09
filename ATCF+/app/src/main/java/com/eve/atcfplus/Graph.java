package com.eve.atcfplus;

import android.content.Context;

import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Graph {
    Context c;
    org.jgrapht.Graph<Node, DefaultEdge> g;

    public List<Node> getNodes() {
        return nodes;
    }

    List<Node> nodes = new ArrayList<Node>();

    public Graph(Context context) throws JSONException {
        c = context;
        JSONObject mapArray = get_JSON();
        g = new DefaultUndirectedGraph<>(DefaultEdge.class);

        genNodes(get_JSON());
        System.out.println(g.toString() + nodes.toString());
        System.out.println(mapArray.toString());

//        testFunction();
    }

    public JSONObject get_JSON() {
        String json = null;
        JSONObject mapArray = null;
        try {
            InputStream is = c.getAssets().open("studienstrecke.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
            mapArray = new JSONObject(json);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return mapArray;
    }

    public void genNodes(JSONObject jsonArray) throws JSONException {
        JSONArray streets = jsonArray.getJSONArray("streets");
        for (int i = 0; i < streets.length(); i++) {
            JSONObject obj = streets.getJSONObject(i);

            Node n = new Node(i, obj.getDouble("lat"), obj.getDouble("lng"), obj.getInt("indicator"));
            nodes.add(n);
            g.addVertex(n);
        }
        JSONArray edges = jsonArray.getJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.length(); i++) {
                JSONArray a = edges.getJSONArray(i);

                g.addEdge(nodes.get(a.getInt(0) - 1), nodes.get(a.getInt(1) - 1));
            }
        }


    }

    //hardcoded there is just one goal in provided json -> testing for valid path works, every other path isn't valid
    public List<Node> returnAllIndicatedStreetsNearby(int recentPosition, int previousPosition) {
        List<Node> indicatorStreets = new ArrayList<Node>();


        Node previous = nodes.get(previousPosition);

        Node start = g
                .vertexSet().stream().filter(Node -> Node.name == recentPosition).findAny()
                .get();

//        List<Node> next = Graphs.successorListOf(g, start);
        List<Node> next = Graphs.neighborListOf(g, start);

        for (Node n : next) {
            if (n.indicator == 0) {
                System.out.println(n.name + " is a good way");
            } else {
                indicatorStreets.add(n);
                System.out.println(n.name + " is a deadend");
            }
        }
        return indicatorStreets;
    }
}
