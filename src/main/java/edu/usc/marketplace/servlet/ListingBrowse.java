package edu.usc.marketplace.servlet;

import edu.usc.marketplace.dao.*;
import edu.usc.marketplace.exception.*;
import edu.usc.marketplace.model.*;
import edu.usc.marketplace.service.AccessPolicy;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request validation and personalized public views for sections 9 and 10. */
final class ListingBrowse {
    private final ListingDAO listings = new ListingDAO();
    private final SavedDAO saved = new SavedDAO();
    private final PhotoDAO photos = new PhotoDAO();
    private final ReviewDAO reviews = new ReviewDAO();
    private final ConversationDAO conversations = new ConversationDAO();
    private final UserDAO users = new UserDAO();

    static ValidationException invalid(String name, String message) {
        return new ValidationException(message, Map.of(name, message));
    }

    static Integer number(HttpServletRequest req, String name, int low, int high) {
        String raw = req.getParameter(name);
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value >= low && value <= high) return value;
        } catch (NumberFormatException ignored) {}
        throw invalid(name, "Enter a whole number from " + low + " to " + high + ".");
    }

    record Card(int listingId, int version, String title, int priceDollars, String category, String status,
                int sellerId, String sellerFirstName, LocalDateTime createdAt, String firstPhotoUrl, boolean isSaved) {
        static Card from(Listing listing, boolean saved) {
            ListingCard card = ListingCard.from(listing);
            String firstName = listing.getSellerName().trim().split("\\s+", 2)[0];
            return new Card(card.id(), listing.getVersion(), card.title(), card.price(), card.category(), card.status(),
                    listing.getSellerId(), firstName, card.createdAt(), card.coverUrl(), saved);
        }
    }

    static SearchQuery parse(HttpServletRequest req) {
        Integer min = number(req, "minPrice", 0, 10000);
        Integer max = number(req, "maxPrice", 0, 10000);
        if (min != null && max != null && min > max) throw invalid("maxPrice", "Maximum price must be at least the minimum.");
        Integer days = number(req, "days", 1, 30);
        if (days != null && !List.of(1,7,30).contains(days)) throw invalid("days", "Choose 24 hours, 7 days or 30 days.");
        String category = req.getParameter("category");
        Category choice = Category.fromString(category);
        if (category != null && !category.isBlank() && choice == null) throw invalid("category", "Choose a listed category.");
        String sort = req.getParameter("sort");
        if (sort != null && !List.of("newest","price_low","price_high").contains(sort)) throw invalid("sort", "Choose a listed sort order.");
        Integer rating = number(req, "minRating", 1, 5);
        Integer page = number(req, "page", 1, Integer.MAX_VALUE);
        return new SearchQuery().keyword(req.getParameter("keyword")).sort(sort)
                .category(choice == null ? null : choice.name()).minPrice(min).maxPrice(max)
                .minRating(rating == null ? null : rating.doubleValue()).postedWithinDays(days)
                .showSold("true".equals(req.getParameter("showSold"))).page(page == null ? 1 : page);
    }

    Object search(HttpServletRequest req) {
        ListingDAO.Page result = listings.searchPage(parse(req), SessionUtil.currentUserId(req));
        var cards = result.listings().stream()
                .map(l -> Card.from(l, result.savedIds().contains(l.getId()))).toList();
        return Map.of("cards", cards, "totalCount", result.totalCount(), "pageNumber", result.pageNumber(), "hasNextPage", result.hasNextPage());
    }

    record Permissions(boolean canMessage, String messageReason, boolean canSave, String saveReason,
                       boolean canReport, String reportReason, boolean canChangeStatus) {}

    static Permissions permissions(Visitor viewer, Listing l, boolean isSaved, LocalDateTime now) {
        boolean own = viewer instanceof Member m && m.owns(l.getSellerId());
        String message = AccessPolicy.check(viewer, Action.MESSAGE, now);
        String save = AccessPolicy.check(viewer, Action.SAVE, now);
        String report = AccessPolicy.check(viewer, Action.REPORT, now);
        if (message == null && own) message = "This is your listing.";
        if (message == null && !l.getStatus().canMessage()) message = "Messaging is turned off for this listing.";
        if (save == null && own) save = "This is your listing.";
        if (save == null && !isSaved && !l.getStatus().isPublic()) save = "This listing is no longer available.";
        return new Permissions(message == null, message, save == null, save, report == null, report,
                own && AccessPolicy.check(viewer, Action.EDIT_OWN, now) == null);
    }

    record BuyerChoice(int id, String name) {}
    record Popup(ListingDetail details, Double sellerRating, int reviewCount, boolean isSaved,
                 Permissions permissions, boolean saleRecorded, List<BuyerChoice> choices) {}

    Popup popup(HttpServletRequest req, int id) {
        Visitor viewer = SessionUtil.loadVisitor(req, users);
        return Database.get().inTransaction(c -> popup(c, viewer, id));
    }

    private Popup popup(java.sql.Connection conn, Visitor viewer, int id) throws java.sql.SQLException {
        Member m = viewer instanceof Member member ? member : null;
        Listing l = listings.findById(conn, id);
        if (l == null || !l.visibleTo(m)) {
            throw new NotFoundException("This listing is unavailable.");
        }
        boolean isSaved = m != null && saved.isSaved(conn, m.getId(), id);
        Permissions permissions = permissions(viewer, l, isSaved, LocalDateTime.now());
        boolean recorded = permissions.canChangeStatus() && l.getBuyerId() != null;
        List<BuyerChoice> choices = permissions.canChangeStatus() && !recorded
                ? conversations.findBuyersForListing(conn, id, m.getId()).stream().map(c -> new BuyerChoice(c.getBuyerId(), c.getOtherName())).toList()
                : List.of();
        return new Popup(ListingDetail.from(l, photos.findByListing(conn, id), m), l.getSellerRating(),
                reviews.count(conn, l.getSellerId()), isSaved, permissions, recorded, choices);
    }
}
