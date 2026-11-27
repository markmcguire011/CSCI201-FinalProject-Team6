package section7;

import edu.usc.marketplace.dao.PhotoDAO;

import java.sql.Connection;
import java.util.List;

/** A PhotoDAO that records the photo rows instead of writing to MySQL. */
class FakePhotoDAO extends PhotoDAO {
    Integer listingId;
    List<String> filePaths;
    int insertCalls;
    int replaceCalls;
    RuntimeException fail;            // when set, insertAll throws it
    FakeTransactions transactions;    // when set, records whether insertAll ran inside a transaction

    boolean insertedInTransaction;

    FakePhotoDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public void insertAll(Connection c, int listingId, List<String> filePaths) {
        insertCalls++;
        insertedInTransaction = transactions != null && transactions.running;
        if (fail != null) throw fail;
        this.listingId = listingId;
        this.filePaths = List.copyOf(filePaths);
    }

    /** Creating a listing must not call this (its DELETE caused deadlocks); counted so tests can check. */
    @Override
    public void replaceAll(Connection c, int listingId, List<String> filePaths) {
        replaceCalls++;
    }
}
