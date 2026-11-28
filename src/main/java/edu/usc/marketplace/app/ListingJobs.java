package edu.usc.marketplace.app;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.ListingActions;
import edu.usc.marketplace.service.Mailer;
import edu.usc.marketplace.servlet.ListingEvents;

import java.util.concurrent.*;
import java.util.logging.*;

/** Notifications commit with the change. Broadcast and review email are retried from the durable event. */
final class ListingJobs implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(ListingJobs.class.getName());
    private final ScheduledExecutorService worker = Executors.newScheduledThreadPool(2);
    private final Database db;

    ListingJobs(Database db) {
        this.db = db;
        worker.scheduleWithFixedDelay(() -> run(() -> new ListingActions().expireInactiveListings()), 0, 1, TimeUnit.DAYS);
        worker.scheduleWithFixedDelay(() -> run(this::deliverBatch), 0, 300, TimeUnit.MILLISECONDS);
    }

    private static void run(Runnable task) {
        try { task.run(); }
        catch (RuntimeException ex) { LOG.log(Level.WARNING, "Listing background task failed.", ex); }
    }

    private void deliverBatch() {
        for (int i = 0; i < 50 && !Thread.currentThread().isInterrupted(); i++) {
            boolean found = db.inTransaction(c -> {
                String sql = "SELECT e.*, u.email FROM listing_events e LEFT JOIN users u ON u.user_id=e.recipient_id "
                        + "WHERE e.delivered=FALSE AND e.next_attempt_at<=NOW() ORDER BY e.event_id LIMIT 1 FOR UPDATE OF e SKIP LOCKED";
                try (var select = c.prepareStatement(sql); var row = select.executeQuery()) {
                    if (!row.next()) return false;
                    long id = row.getLong("event_id");
                    int listing = row.getInt("listing_id");
                    boolean done;
                    if ("BuyerReviewRequested".equals(row.getString("event_type"))) {
                        done = Mailer.get().isConfigured() && Mailer.get().send(row.getString("email"),
                                "Review your USC Marketplace purchase", "Your purchase of listing #" + listing
                                + " is complete. Open USC Marketplace to review the seller.", "listing-event-" + id);
                    } else {
                        String type = switch (row.getString("event_type")) {
                            case "ListingCreated" -> "listing.created";
                            case "ListingUpdated" -> "listing.updated";
                            default -> "listing.status";
                        };
                        ListingEvents.publish(type, id, listing, row.getInt("version"));
                        done = true;
                    }
                    try (var update = c.prepareStatement("UPDATE listing_events SET delivered=?, attempts=attempts+1, "
                            + "next_attempt_at=TIMESTAMPADD(MINUTE, LEAST(60, attempts+1), NOW()) WHERE event_id=?")) {
                        update.setBoolean(1, done);
                        update.setLong(2, id);
                        update.executeUpdate();
                    }
                }
                return true;
            });
            if (!found) return;
        }
    }

    public void close() {
        worker.shutdownNow();
        try { worker.awaitTermination(15, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
