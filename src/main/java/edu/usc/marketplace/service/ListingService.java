package edu.usc.marketplace.service;

import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.PhotoDAO;
import edu.usc.marketplace.exception.ValidationException;
import edu.usc.marketplace.model.Category;
import edu.usc.marketplace.model.ItemCondition;
import edu.usc.marketplace.model.Listing;
import edu.usc.marketplace.model.ListingForm;
import edu.usc.marketplace.model.Member;
import edu.usc.marketplace.model.UploadedFile;
import edu.usc.marketplace.storage.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.IIOException;

/**
 * Creating a listing (section 7).
 *
 *   1. The form and photos are checked first; an invalid form writes no file and runs no SQL.
 *   2. The photos are saved, in the order the seller chose.
 *   3. The listing and its photo rows are saved in one transaction, so a listing never exists without
 *      its photos. A transaction MySQL stops with a deadlock is tried again (see saveListing). If it still
 *      fails, the transaction is rolled back and the saved files are deleted.
 *
 * The seller is always the logged-in member, never someone named in the form.
 */
public class ListingService {
    private static final Logger LOG = Logger.getLogger(ListingService.class.getName());

    /** How many times a transaction stopped by a deadlock is tried in all. */
    public static final int MAX_ATTEMPTS = 4;
    private static final int MYSQL_DEADLOCK = 1213;

    /** Runs statements as one transaction. The app uses Database.inTransaction; tests run the work directly. */
    @FunctionalInterface
    public interface Transactions {
        <T> T inTransaction(Database.SqlWork<T> work);
    }

    private final ListingDAO listings;
    private final PhotoDAO photos;
    private final FileStorageService storage;
    private final Transactions transactions;

    /** Used by the app: the shared database pool and upload folder. */
    public ListingService() {
        this(new ListingDAO(), new PhotoDAO(), FileStorageService.get(), Database.get()::inTransaction);
    }

    /** Used by tests. */
    public ListingService(ListingDAO listings, PhotoDAO photos, FileStorageService storage, Transactions transactions) {
        this.listings = listings;
        this.photos = photos;
        this.storage = storage;
        this.transactions = transactions;
    }

    /** Creates an Available listing for the seller and returns its id. */
    public int create(Member seller, ListingForm form) {
        Map<String, String> errors = new LinkedHashMap<>(Validator.validateListing(form));
        errors.putAll(Validator.validatePhotos(0, form.newPhotos));
        if (!errors.isEmpty()) throw new ValidationException(Validator.FIX_FIELDS, errors);

        Listing listing = new Listing();
        listing.setSellerId(seller.getId());
        listing.setTitle(form.title.trim());
        listing.setCategory(Category.fromString(form.category).name());
        listing.setPrice(Validator.parsePrice(form.price));
        listing.setItemCondition(ItemCondition.fromString(form.condition).name());
        listing.setCourseCode(Validator.normalizeCourseCode(form.courseCode));
        listing.setPickupSpot(form.pickupSpot.trim());
        listing.setDescription(form.description.trim());

        List<String> saved = savePhotos(form.newPhotos);
        int id;
        try {
            id = saveListing(listing, saved);
        } catch (RuntimeException e) {
            deleteQuietly(saved);
            throw e;
        }

        // TODO section 12 (Live feed): broadcast listing.created so open Browse pages show the new listing.
        return id;
    }

    /**
     * Inserts the listing and its photo rows in one transaction.
     *
     * The photo rows are added with PhotoDAO.insertAll, not replaceAll: replaceAll's DELETE on a brand-new
     * listing made simultaneous creates deadlock. If MySQL still stops a transaction with a deadlock (error 1213),
     * it rolls the transaction back completely and asks for it to be restarted, so it is tried again, up to
     * MAX_ATTEMPTS times, after a short random pause. Any other error is not retried.
     */
    private int saveListing(Listing listing, List<String> savedPhotos) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transactions.inTransaction(c -> {
                    int newId = listings.insert(c, listing);
                    photos.insertAll(c, newId, savedPhotos);
                    return newId;
                });
            } catch (DataException e) {
                if (attempt >= MAX_ATTEMPTS || !isDeadlock(e)) throw e;
                LOG.info("Deadlock while creating a listing, trying again (attempt " + (attempt + 1) + " of " + MAX_ATTEMPTS + ").");
                pauseBeforeRetry(attempt, e);
            }
        }
    }

    /** MySQL's "Deadlock found when trying to get lock; try restarting transaction". */
    static boolean isDeadlock(DataException e) {
        SQLException sql = e.getSqlException();
        return sql != null && (sql.getErrorCode() == MYSQL_DEADLOCK || "40001".equals(sql.getSQLState()));
    }

    /** 20 to 80 ms times the attempt number, so the retries of requests that collided do not collide again. */
    private static void pauseBeforeRetry(int attempt, DataException cause) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(20, 81) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    /**
     * Saves each photo and returns the saved file names in the same order. If one fails, the ones
     * already saved are deleted, so a failed request leaves no files behind.
     */
    private List<String> savePhotos(List<UploadedFile> files) {
        List<String> saved = new ArrayList<>();
        if (files == null) return saved;
        for (UploadedFile file : files) {
            if (file == null) continue;
            try (InputStream in = file.stream()) {
                saved.add(storage.saveImage(file.fileName(), in));
            } catch (ValidationException | IIOException e) {
                // It starts like a JPG or PNG but cannot be opened as a picture. saveImage reports that as a
                // ValidationException, or as an IIOException when ImageIO fails partway through the file.
                deleteQuietly(saved);
                throw new ValidationException(Validator.FIX_FIELDS,
                        Map.of("photos", "\"" + file.fileName() + "\": " + Validator.PHOTO_TYPE_ERROR));
            } catch (IOException e) {
                deleteQuietly(saved);
                throw new UncheckedIOException("Could not save a listing photo", e);
            } catch (RuntimeException e) {
                deleteQuietly(saved);
                throw e;
            }
        }
        return saved;
    }

    /** A file that cannot be deleted is only logged, so the original error is the one the user sees. */
    private void deleteQuietly(List<String> fileNames) {
        for (String name : fileNames) {
            try {
                storage.deletePublic(name);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Could not delete the unused photo " + name, e);
            }
        }
    }
}
