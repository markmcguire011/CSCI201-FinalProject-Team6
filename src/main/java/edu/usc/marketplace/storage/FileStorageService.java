package edu.usc.marketplace.storage;

import edu.usc.marketplace.exception.NotFoundException;
import edu.usc.marketplace.exception.ValidationException;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Saves uploaded files on the server (section 15). The database stores only the file name.
 *
 * - Photos go to uploads/public and can be viewed by anyone through PhotoServlet.
 * - Payment proofs go to uploads/private and can only be opened by administrators (ProofServlet).
 * - Every file is saved under a random name (a UUID), so names cannot be guessed and two uploads
 *   called "photo.jpg" never overwrite each other.
 * - The real file type is read from the file's first bytes, not from its name, so a renamed text
 *   file cannot pretend to be an image.
 * - Photos wider than 1200 px are resized when uploaded so pages load faster (section 16).
 */
public class FileStorageService {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final int MAX_WIDTH = 1200;

    private static volatile FileStorageService instance;

    private final Path publicDir;
    private final Path privateDir;

    /** uploadDir gets two folders: public and private. They are created if missing. */
    public FileStorageService(Path uploadDir) throws IOException {
        this(uploadDir.resolve("public"), uploadDir.resolve("private"));
    }

    public FileStorageService(Path publicDir, Path privateDir) throws IOException {
        this.publicDir = Files.createDirectories(publicDir).toAbsolutePath().normalize();
        this.privateDir = Files.createDirectories(privateDir).toAbsolutePath().normalize();
    }

    /** Called once by AppStartup. */
    public static void init(FileStorageService storage) {
        instance = storage;
    }

    public static FileStorageService get() {
        FileStorageService s = instance;
        if (s == null) throw new IllegalStateException("File storage has not been started.");
        return s;
    }

    /**
     * Saves a listing or profile photo (JPG or PNG, at most 5 MB). Returns the saved file name.
     * originalName is the name on the user's computer; it is not used, because the file gets a random name.
     */
    public String saveImage(String originalName, InputStream in) throws IOException {
        byte[] data = readLimited(in);
        String ext = detectType(data);
        if (!"png".equals(ext) && !"jpg".equals(ext)) {
            throw new ValidationException("Photos must be JPG or PNG files.");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) throw new ValidationException("Photos must be JPG or PNG files.");

        String name = UUID.randomUUID() + "." + ext;
        Path target = publicDir.resolve(name);
        if (image.getWidth() > MAX_WIDTH) {
            ImageIO.write(resize(image, "png".equals(ext)), "png".equals(ext) ? "png" : "jpeg", target.toFile());
        } else {
            Files.write(target, data);
        }
        return name;
    }

    /** Saves a proof of payment (JPG, PNG or PDF, at most 5 MB) in the private folder. Returns the saved file name. */
    public String saveProof(String originalName, InputStream in) throws IOException {
        byte[] data = readLimited(in);
        String ext = detectType(data);
        if (ext == null) throw new ValidationException("Proof of payment must be a JPG, PNG, or PDF file.");
        String name = UUID.randomUUID() + "." + ext;
        Files.write(privateDir.resolve(name), data);
        return name;
    }

    /** Finds a public photo. A name that points outside the folder (e.g. "../private/x.pdf") is treated as missing. */
    public Path open(String name) {
        return find(publicDir, name, "Photo not found.");
    }

    /** Finds a payment proof. Only ProofServlet calls this, after checking that the user is an administrator. */
    public Path openPrivate(String name) {
        return find(privateDir, name, "File not found.");
    }

    /** Deletes a photo that is no longer used, e.g. after a listing's photos are replaced. */
    public void deletePublic(String name) throws IOException {
        Path p = publicDir.resolve(name).normalize();
        if (p.startsWith(publicDir) && !p.equals(publicDir)) Files.deleteIfExists(p);
    }

    /** Roll back a staged upload if the listing transaction fails. */
    public void deleteProof(String name) throws IOException {
        Files.deleteIfExists(find(privateDir, name, "File not found."));
    }

    public String contentType(Path file) {
        String n = file.getFileName().toString();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "image/jpeg";
    }

    private static Path find(Path folder, String name, String notFoundMessage) {
        if (name == null || name.isBlank()) throw new NotFoundException(notFoundMessage);
        Path p = folder.resolve(name).normalize();
        if (!p.startsWith(folder) || p.equals(folder) || !Files.isRegularFile(p)) {
            throw new NotFoundException(notFoundMessage);
        }
        return p;
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BYTES + 1);
        if (data.length > MAX_BYTES) throw new ValidationException("Each file must be 5 MB or smaller.");
        if (data.length == 0) throw new ValidationException("The file is empty.");
        return data;
    }

    /** Reads the file's first bytes ("magic numbers") to find its real type. */
    static String detectType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "png";
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "jpg";
        if (d.length >= 5 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F' && d[4] == '-') return "pdf";
        return null;
    }

    /** Shrinks the image to 1200 px wide, keeping its shape. */
    private static BufferedImage resize(BufferedImage image, boolean keepTransparency) {
        int height = Math.round(image.getHeight() * (MAX_WIDTH / (float) image.getWidth()));
        int type = keepTransparency ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage small = new BufferedImage(MAX_WIDTH, height, type);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(image, 0, 0, MAX_WIDTH, height, null);
        g.dispose();
        return small;
    }
}
