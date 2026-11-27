package section7;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.model.Listing;

import java.sql.Connection;

/** A ListingDAO that records the inserted listing instead of writing to MySQL. */
class FakeListingDAO extends ListingDAO {
    static final int NEW_ID = 31;

    Listing inserted;
    int insertCalls;
    FakeTransactions transactions;   // when set, records whether insert ran inside a transaction

    boolean insertedInTransaction;

    FakeListingDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public int insert(Connection c, Listing l) {
        insertCalls++;
        inserted = l;
        insertedInTransaction = transactions != null && transactions.running;
        return NEW_ID;
    }
}
