package edu.usc.marketplace.servlet;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Public invalidations only. Subscribers reload through the normal authorized HTTP endpoints. */
@ServerEndpoint("/live")
public class ListingEvents {
    private static final Set<Session> clients = ConcurrentHashMap.newKeySet();
    @OnOpen public void open(Session client) { clients.add(client); }
    @OnClose public void close(Session client) { clients.remove(client); }

    public static void publish(String type, long eventId, int listingId, int version) {
        String event = "{\"type\":\"" + type + "\",\"data\":{\"eventId\":" + eventId
                + ",\"id\":" + listingId + ",\"version\":" + version + "}}";
        for (Session client : clients) {
            if (client.isOpen()) client.getAsyncRemote().sendText(event);
        }
    }
}
