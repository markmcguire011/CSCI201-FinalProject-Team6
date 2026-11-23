package edu.usc.marketplace.servlet;

import edu.usc.marketplace.storage.FileStorageService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * GET /admin/proofs/{file name}: opens a proof of payment from uploads/private.
 * Only administrators can open these files (section 15), and the browser is told not to keep a copy.
 */
@WebServlet("/admin/proofs/*")
public class ProofServlet extends BaseServlet {
    private static final long serialVersionUID = 1L;

    private transient FileStorageService storage;

    public ProofServlet() {
    }

    public ProofServlet(FileStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void init() {
        if (storage == null) storage = FileStorageService.get();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        requireAdmin(req);
        String info = req.getPathInfo();
        Path proof = storage.openPrivate(info == null ? "" : info.substring(1));
        resp.setContentType(storage.contentType(proof));
        resp.setContentLengthLong(Files.size(proof));
        resp.setHeader("Cache-Control", "private, no-store");
        // Show it in the browser instead of downloading it.
        resp.setHeader("Content-Disposition", "inline");
        Files.copy(proof, resp.getOutputStream());
    }
}
