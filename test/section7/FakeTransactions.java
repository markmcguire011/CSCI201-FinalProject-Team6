package section7;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.service.ListingService;

import java.sql.SQLException;

/** Runs the work right away with no Connection, and counts how many transactions were started. */
class FakeTransactions implements ListingService.Transactions {
    int calls;
    boolean running;   // true while the work is running

    @Override
    public <T> T inTransaction(Database.SqlWork<T> work) {
        calls++;
        running = true;
        try {
            return work.run(null);
        } catch (SQLException e) {
            throw new DataException(e);   // what Database.inTransaction does
        } finally {
            running = false;
        }
    }
}
