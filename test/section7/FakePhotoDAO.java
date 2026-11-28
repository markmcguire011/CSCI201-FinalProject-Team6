package section7;

import edu.usc.marketplace.dao.PhotoDAO;
import edu.usc.marketplace.model.ListingPhoto;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A PhotoDAO that keeps photo rows in a map and records changes instead of writing to MySQL. */
class FakePhotoDAO extends PhotoDAO {
    final Map<Integer, List<ListingPhoto>> stored = new HashMap<>();   // what findByListing returns

    // insertAll (creating)
    Integer listingId;
    List<String> filePaths;
    int insertCalls;
    RuntimeException fail;            // when set, insertAll throws it
    boolean insertedInTransaction;

    // replaceAll (editing)
    Integer replacedListingId;
    List<String> replacedPaths;
    int replaceCalls;
    RuntimeException failReplace;     // when set, replaceAll throws it
    boolean replacedInTransaction;

    FakeTransactions transactions;    // when set, records whether the writes ran inside a transaction
    private int nextPhotoId = 900;

    FakePhotoDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public List<ListingPhoto> findByListing(int listingId) {
        return List.copyOf(stored.getOrDefault(listingId, List.of()));
    }

    @Override
    public void insertAll(Connection c, int listingId, List<String> filePaths) {
        insertCalls++;
        insertedInTransaction = transactions != null && transactions.running;
        if (fail != null) throw fail;
        this.listingId = listingId;
        this.filePaths = List.copyOf(filePaths);
    }

    /** Like the real one: the listing's rows become exactly these files, with new ids, in this order. */
    @Override
    public void replaceAll(Connection c, int listingId, List<String> filePaths) {
        replaceCalls++;
        replacedInTransaction = transactions != null && transactions.running;
        if (failReplace != null) throw failReplace;
        replacedListingId = listingId;
        replacedPaths = List.copyOf(filePaths);
        List<ListingPhoto> rows = new ArrayList<>();
        for (int i = 0; i < filePaths.size(); i++) rows.add(photo(nextPhotoId++, listingId, filePaths.get(i), i + 1));
        stored.put(listingId, rows);
    }

    static ListingPhoto photo(int id, int listingId, String file, int sortOrder) {
        ListingPhoto p = new ListingPhoto();
        p.setId(id);
        p.setListingId(listingId);
        p.setFilePath(file);
        p.setSortOrder(sortOrder);
        return p;
    }
}
