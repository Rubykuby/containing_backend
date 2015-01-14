package org.nhl.containing_backend.communication.messages;

import org.nhl.containing_backend.cranes.Crane;
import org.nhl.containing_backend.vehicles.Agv;

/**
 *
 */
public class MoveMessage extends Message {

    private Agv agv;
    private String dijkstra;
    private Crane crane;
    private int endLocationId;

    public MoveMessage(Agv agv, String dijkstra, Crane crane) {
        super(Message.MOVE);
        this.agv = agv;
        this.dijkstra = dijkstra;
        this.crane = crane;
        this.endLocationId = crane.getId();
    }

    @Override
    public String generateXml() {
        String message = "";
        message += "<id>" + getId() + "</id>";
        message += "<Move>";
        message += "<AgvId>" + agv.getId() + "</AgvId>";
        message += "<CurrentX>" + agv.getX() + "</CurrentX>";
        message += "<CurrentY>" + agv.getY() + "</CurrentY>";
        message += "<Dijkstra>" + dijkstra + "</Dijkstra>";
        message += "<EndLocationType>" + crane.getType() + "</EndLocationType>";
        message += "<EndLocationId>" + crane.getId() + "</EndLocationId>";
        message += "</Move>";
        this.endLocationId = crane.getId();
        return message;
    }

    public Agv getAgv() {
        return agv;
    }

    public int getEndLocationId() {
        return endLocationId;
    }
}
