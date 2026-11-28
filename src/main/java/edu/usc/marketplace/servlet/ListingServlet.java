package edu.usc.marketplace.servlet;

import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.dao.ListingActions;
import edu.usc.marketplace.model.ListingStatus;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.service.ListingService;
import edu.usc.marketplace.service.Validator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Listings (section 7).
 *
 *   POST /api/listings        the logged-in member creates a listing. Multipart form: title, category, price,
 *                             condition, courseCode, pickupSpot, description, and 0 to 5 files named "photos",
 *                             in the order they should be shown. Answers 201 {"id": 31}.
 *   GET  /api/listings/{id}   anyone: one listing (ListingDetail), for the Edit Listing form and the pop-up.
 *   POST /api/listings/{id}   the seller edits it. The same fields as creating, plus "version" (from the GET),
 *                             "keepPhotoIds" repeated once per kept photo in display order, and the new files
 *                             named "newPhotos". Optionally "photoOrder", repeated in display order, each
 *                             "keep:<photo id>" or "new:<index into newPhotos>"; without it the kept photos come
 *                             first, then the new ones. Answers 200 with the ListingDetail.
 *
 * AuthFilter has already refused guests, unverified and suspended accounts for both POSTs (SELL and EDIT_OWN).
 *
 * GET the collection to search, or /{id}/popup for the personalized dialog.
 * POST /{id}/status (multipart), /{id}/saved (desired state), or /{id}/conversation.
 */
@WebServlet({"/api/listings", "/api/listings/*"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 26 * 1024 * 1024, fileSizeThreshold = 0)
public class ListingServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String NOT_FOUND = "Page not found.";
    public static final String TOO_LARGE_IN_TOTAL = "The photos are too large together. Add up to 5 photos of 5 MB or smaller each.";

    /** Stands for a keepPhotoIds value that is not a number; no photo has this id, so it is reported as out of date. */
    private static final int NOT_A_PHOTO_ID = -1;

    private transient ListingService listings;

    private transient ListingBrowse browse;
    private transient ListingActions actions;

    /** Used by Tomcat. */
    public ListingServlet() {
    }

    /** Used by tests. */
    public ListingServlet(ListingService listings) {
        this.listings = listings;
    }

    @Override
    public void init() {
        if (listings == null) listings = new ListingService();
        browse = new ListingBrowse();
        actions = new ListingActions();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (isCollection(req)) {
            sendJson(resp, browse.search(req));
            return;
        }
        if (req.getPathInfo().matches("/[0-9]+/popup")) {
            sendJson(resp, browse.popup(req, pathId(req)));
            return;
        }
        // Refresh the database role before allowing access to a hidden listing.
        Member viewer = SessionUtil.loadVisitor(req, new edu.usc.marketplace.dao.UserDAO()) instanceof Member m ? m : null;
        sendJson(resp, listings.getListing(viewer, listingId(req)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        boolean creating = isCollection(req);

        if (!(SessionUtil.visitor(req) instanceof Member member)) throw new AuthException(SessionUtil.LOGIN_REQUIRED);

        if (!creating && req.getPathInfo().matches("/[0-9]+/(status|saved|conversation)")) {
            int id = pathId(req);
            String action = req.getPathInfo().substring(req.getPathInfo().lastIndexOf('/') + 1);
            switch (action) {
                case "saved" -> {
                    SaveRequest body = readJson(req, SaveRequest.class);
                    if (body.isSaved == null) throw new ValidationException("Choose a saved state.");
                    sendJson(resp, Map.of("isSaved", actions.setSaved(member.getId(), id, body.isSaved)));
                }
                case "conversation" -> sendJson(resp, Map.of("conversationId", actions.startConversation(member.getId(), id)));
                case "status" -> {
                    List<UploadedFile> proof = readPhotos(req, "proof");
                    if (proof.size() > 1) throw new ValidationException("Choose one payment proof.");
                    ListingStatus status;
                    try { status = ListingStatus.valueOf(String.valueOf(req.getParameter("status")).trim().toUpperCase(java.util.Locale.ROOT)); }
                    catch (IllegalArgumentException e) { throw new ValidationException("Choose a seller status."); }
                    sendJson(resp, actions.change(member, id, status, version(req),
                            ListingBrowse.number(req, "buyerId", 1, Integer.MAX_VALUE), proof.isEmpty() ? null : proof.get(0)));
                }
            }
            return;
        }
        int id = creating ? 0 : listingId(req);
        // Read the files first: if Tomcat refused the upload as too large, getParts() says so,
        // while getParameter() would just return null and every field would look empty.
        List<UploadedFile> photos = readPhotos(req, creating ? "photos" : "newPhotos");

        ListingForm form = new ListingForm();
        form.title = req.getParameter("title");
        form.category = req.getParameter("category");
        form.price = req.getParameter("price");
        form.condition = req.getParameter("condition");
        form.courseCode = req.getParameter("courseCode");
        form.pickupSpot = req.getParameter("pickupSpot");
        form.description = req.getParameter("description");
        form.newPhotos = photos;

        if (creating) {
            sendJson(resp, HttpServletResponse.SC_CREATED, Map.of("id", listings.create(member, form)));
        } else {
            form.version = version(req);
            form.keepPhotoIds = keepPhotoIds(req);
            String[] order = req.getParameterValues("photoOrder");
            form.photoOrder = order == null ? null : List.of(order);
            sendJson(resp, listings.update(member, id, form));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        throw new NotFoundException(NOT_FOUND);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        throw new NotFoundException(NOT_FOUND);
    }

    private record SaveRequest(Boolean isSaved) {}

    /** /api/listings or /api/listings/ (no id). */
    private static boolean isCollection(HttpServletRequest req) {
        String path = req.getPathInfo();
        return path == null || path.equals("/");
    }

    /** The id in /api/listings/{id}. Anything that is not a whole number is "Listing not found.". */
    private static int listingId(HttpServletRequest req) {
        String path = req.getPathInfo().substring(1);
        try {
            return Integer.parseInt(path);
        } catch (NumberFormatException e) {
            throw new NotFoundException(ListingService.NOT_FOUND);
        }
    }

    /**
     * The version the edit page loaded. A missing or broken value can never match the database, so the
     * edit is refused as "This listing was just changed. Refresh and try again.".
     */
    private static int version(HttpServletRequest req) {
        String v = req.getParameter("version");
        try {
            return v == null ? -1 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The kept photo ids in display order. Blanks are skipped; a value that is not a number cannot match a photo. */
    private static List<Integer> keepPhotoIds(HttpServletRequest req) {
        List<Integer> ids = new ArrayList<>();
        String[] values = req.getParameterValues("keepPhotoIds");
        if (values == null) return ids;
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            try {
                ids.add(Integer.parseInt(v.trim()));
            } catch (NumberFormatException e) {
                ids.add(NOT_A_PHOTO_ID);
            }
        }
        return ids;
    }

    /** The non-empty files with the given part name, in the order the page sent them. */
    private static List<UploadedFile> readPhotos(HttpServletRequest req, String partName) throws IOException, ServletException {
        Collection<Part> parts;
        try {
            parts = req.getParts();
        } catch (IllegalStateException e) {
            if (!isUploadTooLarge(e)) throw e;
            if ("proof".equals(partName)) throw ListingBrowse.invalid("proof", "Payment proof must be 5 MB or smaller.");
            String message = isOneFileTooLarge(e) ? Validator.PHOTO_SIZE_ERROR : TOO_LARGE_IN_TOTAL;
            throw new ValidationException(Validator.FIX_FIELDS, Map.of("photos", message));
        }

        List<UploadedFile> photos = new ArrayList<>();
        for (Part part : parts) {
            if (!partName.equals(part.getName()) || part.getSubmittedFileName() == null || part.getSize() == 0) continue;
            try (InputStream in = part.getInputStream()) {
                photos.add(new UploadedFile(part.getSubmittedFileName(), in.readAllBytes()));
            }
        }
        return photos;
    }

    /** One file over maxFileSize, as opposed to the whole request over maxRequestSize. */
    private static boolean isOneFileTooLarge(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getClass().getSimpleName().startsWith("FileSizeLimitExceeded")) return true;
        }
        return false;
    }
}
