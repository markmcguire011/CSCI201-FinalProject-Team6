package section7;

import edu.usc.marketplace.dao.PhotoDAO;

import java.sql.Connection;
import java.util.List;

/** A PhotoDAO that records the photo rows instead of writing to MySQL. */
class FakePhotoDAO extends PhotoDAO {
    Integer listingId;
    List<String> filePaths;
    int replaceCalls;
    RuntimeException fail;            // when set, replaceAll throws it
    FakeTransactions transactions;    // when set, records whether replaceAll ran inside a transaction

    boolean replacedInTransaction;

    FakePhotoDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public void replaceAll(Connection c, int listingId, List<String> filePaths) {
        replaceCalls++;
        replacedInTransaction = transactions != null && transactions.running;
        if (fail != null) throw fail;
        this.listingId = listingId;
        this.filePaths = List.copyOf(filePaths);
    }
}
