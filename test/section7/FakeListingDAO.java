package section7;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.model.Listing;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/** A ListingDAO that keeps listings in a map and records inserts and updates instead of writing to MySQL. */
class FakeListingDAO extends ListingDAO {
    static final int NEW_ID = 31;

    final Map<Integer, Listing> stored = new HashMap<>();   // what findById returns
    Listing inserted;
    int insertCalls;
    Listing updated;
    Integer updatedExpectedVersion;
    int updateCalls;
    boolean updateResult = true;     // false acts like a version that no longer matches
    FakeTransactions transactions;   // when set, records whether insert and update ran inside a transaction

    boolean insertedInTransaction;
    boolean updatedInTransaction;

    FakeListingDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public Listing findById(int listingId) {
        return stored.get(listingId);
    }

    @Override
    public int insert(Connection c, Listing l) {
        insertCalls++;
        inserted = l;
        insertedInTransaction = transactions != null && transactions.running;
        return NEW_ID;
    }

    /** Like the real UPDATE: on success the stored listing gets the new values and version + 1. */
    @Override
    public boolean update(Connection c, Listing l, int expectedVersion) {
        updateCalls++;
        updated = l;
        updatedExpectedVersion = expectedVersion;
        updatedInTransaction = transactions != null && transactions.running;
        if (!updateResult) return false;
        Listing s = stored.get(l.getId());
        s.setTitle(l.getTitle());
        s.setCategory(l.getCategory());
        s.setPrice(l.getPrice());
        s.setItemCondition(l.getItemCondition());
        s.setCourseCode(l.getCourseCode());
        s.setPickupSpot(l.getPickupSpot());
        s.setDescription(l.getDescription());
        s.setVersion(s.getVersion() + 1);
        return true;
    }
}
