package edu.usc.marketplace.servlet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import edu.usc.marketplace.exception.AppException;
import edu.usc.marketplace.exception.AuthException;
import edu.usc.marketplace.exception.ForbiddenException;
import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every servlet extends this class (section 16).
 *
 * It catches errors in one place and sends them to the browser as {"error": "message"} with the right
 * status code, never as a Java error page:
 *   ValidationException 400, AuthException 401, ForbiddenException 403, NotFoundException 404,
 *   ConflictException 409, anything unexpected 500 "Something went wrong. Please try again."
 * The details of unexpected errors are written to the Tomcat log, not sent to the user.
 *
 * It also has the helpers every servlet needs: reading and sending JSON, checking the login, and reading ids.
 */
public abstract class BaseServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(BaseServlet.class.getName());

    /** Session attributes set by the login servlet. Change these two names here if login uses different ones. */
    public static final String SESSION_USER_ID = "userId";
    public static final String SESSION_ROLE = "role";

    public static final String GENERIC_ERROR = "Something went wrong. Please try again.";

    /** One shared, thread-safe Gson. Dates are sent as text like "2026-11-22T10:15:30". */
    protected static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        try {
            super.service(req, resp);
        } catch (AppException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.getMessage());
            if (e instanceof ValidationException v && !v.getFieldErrors().isEmpty()) {
                body.put("fields", v.getFieldErrors());
            }
            writeError(resp, e.getStatus(), body);
        } catch (IllegalStateException e) {
            // Tomcat throws this when an upload is larger than the servlet's @MultipartConfig allows.
            if (isUploadTooLarge(e)) {
                writeError(resp, 400, Map.of("error", "Each file must be 5 MB or smaller."));
            } else {
                unexpected(req, resp, e);
            }
        } catch (RuntimeException | ServletException e) {
            unexpected(req, resp, e);
        }
    }

    // ----- sending and reading JSON -----

    /** Sends any object as JSON with status 200. */
    protected void sendJson(HttpServletResponse resp, Object body) throws IOException {
        sendJson(resp, HttpServletResponse.SC_OK, body);
    }

    protected void sendJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        // API answers depend on who is logged in, so the browser must not reuse them.
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(GSON.toJson(body));
        resp.getWriter().flush();
    }

    /** Reads the request body as JSON. An empty or broken body becomes a 400 error. */
    protected <T> T readJson(HttpServletRequest req, Class<T> type) throws IOException {
        try (Reader reader = req.getReader()) {
            T value = GSON.fromJson(reader, type);
            if (value == null) throw new ValidationException("The request is empty.");
            return value;
        } catch (JsonParseException e) {
            throw new ValidationException("The request could not be read. Refresh the page and try again.");
        }
    }

    // ----- who is logged in -----

    /** The logged-in user's id, or null for a guest. */
    protected Integer currentUserId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return null;
        Object id = session.getAttribute(SESSION_USER_ID);
        return id instanceof Integer i ? i : null;
    }

    protected boolean isAdmin(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null && "ADMIN".equals(session.getAttribute(SESSION_ROLE));
    }

    /** Returns the user's id, or stops the request with 401 if nobody is logged in. */
    protected int requireLogin(HttpServletRequest req) {
        Integer id = currentUserId(req);
        if (id == null) throw new AuthException("Log in to continue.");
        return id;
    }

    /** Returns the administrator's id, or stops the request with 401 or 403. */
    protected int requireAdmin(HttpServletRequest req) {
        int id = requireLogin(req);
        if (!isAdmin(req)) throw new ForbiddenException("Only administrators can do that.");
        return id;
    }

    // ----- reading ids and numbers -----

    /** The id at the end of the URL, e.g. 42 in /api/listings/42. */
    protected int pathId(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.length() < 2) throw new NotFoundException("Page not found.");
        String first = info.substring(1).split("/")[0];
        try {
            return Integer.parseInt(first);
        } catch (NumberFormatException e) {
            throw new NotFoundException("Page not found.");
        }
    }

    /** A whole-number query parameter such as ?page=2, or defaultValue if it is missing. */
    protected Integer intParam(HttpServletRequest req, String name, Integer defaultValue) {
        String v = req.getParameter(name);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(name + " must be a whole number.");
        }
    }

    // ----- errors -----

    private void unexpected(HttpServletRequest req, HttpServletResponse resp, Exception e) throws IOException {
        LOG.log(Level.SEVERE, "Unexpected error on " + req.getMethod() + " " + req.getRequestURI(), e);
        writeError(resp, 500, Map.of("error", GENERIC_ERROR));
    }

    private void writeError(HttpServletResponse resp, int status, Map<String, Object> body) throws IOException {
        if (resp.isCommitted()) return;   // part of the answer was already sent; nothing more can be added
        resp.resetBuffer();
        sendJson(resp, status, body);
    }

    /** True if Tomcat refused an upload because it is larger than the servlet's @MultipartConfig allows. */
    protected static boolean isUploadTooLarge(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getClass().getSimpleName().contains("SizeLimitExceeded")) return true;
        }
        return false;
    }

    /** Lets Gson read and write LocalDateTime, which it cannot do on its own in Java 17. */
    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) out.nullValue();
            else out.value(value.toString());
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString());
        }
    }
}
