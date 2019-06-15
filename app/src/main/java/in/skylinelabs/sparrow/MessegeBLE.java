package in.skylinelabs.sparrow;

import java.util.ArrayList;

public class MessegeBLE {


    private String data;
    private ArrayList<String> recievedBy = new ArrayList<>();
    private double priority,ttl;

    public MessegeBLE(String data, double priority, double ttl) {
        this.data = data;
        this.priority = priority;
        this.ttl = ttl;
    }

    public String getData() {
        return data;
    }

    boolean isSent(String deviceAddress){
        return recievedBy.contains(deviceAddress);
    }

    void sentTo(String deviceAddress){
        recievedBy.add(deviceAddress);
    }
}
