package edu.usc.marketplace.servlet;

import edu.usc.marketplace.storage.FileStorageService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GET /photos/{file name}: sends a listing or profile photo (section 16).
 *
 * The Cache-Control header lets the browser keep its copy for a day (86,400 seconds), so the same photo
 * is not downloaded again on every page. The file is never changed after upload, because every new
 * upload gets a new random name.
 */
@WebServlet("/photos/*")
public class PhotoServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    public static final String CACHE_ONE_DAY = "public, max-age=86400";

    private transient FileStorageService storage;

    /** Used by Tomcat. */
    public PhotoServlet() {
    }

    /** Used by tests. */
    public PhotoServlet(FileStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void init() {
        if (storage == null) storage = FileStorageService.get();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String info = req.getPathInfo();
        Path photo = storage.open(info == null ? "" : info.substring(1));
        resp.setContentType(storage.contentType(photo));
        resp.setContentLengthLong(Files.size(photo));
        resp.setHeader("Cache-Control", CACHE_ONE_DAY);
        Files.copy(photo, resp.getOutputStream());
    }
}
