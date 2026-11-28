package edu.usc.marketplace.servlet;

import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.EditProfileForm;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.model.Visitor;
import edu.usc.marketplace.service.ProfileService;
import edu.usc.marketplace.service.Validator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Profile pages (section 6).
 *
 *   GET  /api/profile?id=42   anyone: the user's public profile and listings (their hidden listings too, for the owner)
 *   POST /api/profile         the logged-in member edits their own profile. Multipart form:
 *                             fullName, bio, removePhoto ("true" or absent), and an optional file "photo"
 *
 * POST takes no id: it always changes the profile of the member AuthFilter loaded from the database,
 * and AuthFilter has already refused guests, unverified and suspended accounts.
 */
@WebServlet("/api/profile")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024, fileSizeThreshold = 0)
public class ProfileServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private transient ProfileService profiles;

    /** Used by Tomcat. */
    public ProfileServlet() {
    }

    /** Used by tests. */
    public ProfileServlet(ProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public void init() {
        if (profiles == null) profiles = new ProfileService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Browsing: AuthFilter did not look the user up, so this comes from the session. Only the id is used.
        Visitor visitor = SessionUtil.visitor(req);
        Member viewer = visitor instanceof Member m ? m : null;
        sendJson(resp, profiles.getProfile(viewer, req.getParameter("id")));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        if (!(SessionUtil.visitor(req) instanceof Member member)) throw new AuthException(SessionUtil.LOGIN_REQUIRED);

        // Read the file first: if Tomcat refused the upload as too large, getPart() says so,
        // while getParameter() would just return null and the page would show "Enter your full name".
        UploadedFile photo = readPhoto(req);

        EditProfileForm form = new EditProfileForm(req.getParameter("fullName"), req.getParameter("bio"),
                photo, "true".equalsIgnoreCase(req.getParameter("removePhoto")));
        sendJson(resp, profiles.updateProfile(member, form));
    }

    /** The "photo" file, or null if none was chosen (an empty file input still sends an empty part). */
    private static UploadedFile readPhoto(HttpServletRequest req) throws IOException, ServletException {
        Part part;
        try {
            part = req.getPart("photo");
        } catch (IllegalStateException e) {
            if (isUploadTooLarge(e)) {
                throw new ValidationException(ProfileService.FIX_FIELDS, Map.of("photo", Validator.PHOTO_SIZE_ERROR));
            }
            throw e;
        }
        if (part == null || part.getSize() == 0) return null;
        try (InputStream in = part.getInputStream()) {
            return new UploadedFile(part.getSubmittedFileName(), in.readAllBytes());
        }
    }
}
