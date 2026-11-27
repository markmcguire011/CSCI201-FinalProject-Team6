package edu.usc.marketplace.servlet;

import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
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
 *   POST /api/listings   the logged-in member creates a listing. Multipart form: title, category, price,
 *                        condition, courseCode, pickupSpot, description, and 0 to 5 files named "photos",
 *                        in the order they should be shown. Answers 201 {"id": 31}.
 *
 * AuthFilter has already refused guests, unverified and suspended accounts (POST /api/listings is SELL).
 *
 * Every other request answers 404 for now: chunk 5 adds GET /api/listings/{id} and the edit endpoint,
 * and GET /api/listings (search) belongs to the Browse section.
 */
@WebServlet({"/api/listings", "/api/listings/*"})
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 26 * 1024 * 1024, fileSizeThreshold = 0)
public class ListingServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String NOT_FOUND = "Page not found.";
    public static final String TOO_LARGE_IN_TOTAL = "The photos are too large together. Add up to 5 photos of 5 MB or smaller each.";

    private transient ListingService listings;

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
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        String path = req.getPathInfo();
        if (path != null && !path.equals("/")) throw new NotFoundException(NOT_FOUND);
        if (!(SessionUtil.visitor(req) instanceof Member seller)) throw new AuthException(SessionUtil.LOGIN_REQUIRED);

        // Read the files first: if Tomcat refused the upload as too large, getParts() says so,
        // while getParameter() would just return null and every field would look empty.
        List<UploadedFile> photos = readPhotos(req);

        ListingForm form = new ListingForm();
        form.title = req.getParameter("title");
        form.category = req.getParameter("category");
        form.price = req.getParameter("price");
        form.condition = req.getParameter("condition");
        form.courseCode = req.getParameter("courseCode");
        form.pickupSpot = req.getParameter("pickupSpot");
        form.description = req.getParameter("description");
        form.newPhotos = photos;

        int id = listings.create(seller, form);
        sendJson(resp, HttpServletResponse.SC_CREATED, Map.of("id", id));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        throw new NotFoundException(NOT_FOUND);   // chunk 5: GET /api/listings/{id}; Browse section: search
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) {
        throw new NotFoundException(NOT_FOUND);   // chunk 5: editing a listing
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        throw new NotFoundException(NOT_FOUND);
    }

    /** The non-empty "photos" files, in the order the page sent them. */
    private static List<UploadedFile> readPhotos(HttpServletRequest req) throws IOException, ServletException {
        Collection<Part> parts;
        try {
            parts = req.getParts();
        } catch (IllegalStateException e) {
            if (!isUploadTooLarge(e)) throw e;
            String message = isOneFileTooLarge(e) ? Validator.PHOTO_SIZE_ERROR : TOO_LARGE_IN_TOTAL;
            throw new ValidationException(Validator.FIX_FIELDS, Map.of("photos", message));
        }

        List<UploadedFile> photos = new ArrayList<>();
        for (Part part : parts) {
            if (!"photos".equals(part.getName()) || part.getSubmittedFileName() == null || part.getSize() == 0) continue;
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
