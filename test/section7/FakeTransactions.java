package section7;

import edu.usc.marketplace.dao.Database;
import edu.usc.marketplace.dao.DataException;
import edu.usc.marketplace.service.ListingService;

import java.sql.SQLException;

/** Runs the work right away with no Connection, and counts how many transactions were started. */
class FakeTransactions implements ListingService.Transactions {
    int calls;
    boolean running;     // true while the work is running
    int deadlocks;       // the next this-many transactions run the work, then fail the way MySQL reports a deadlock
    RuntimeException failWith;   // when set, every transaction fails with it instead of a deadlock

    @Override
    public <T> T inTransaction(Database.SqlWork<T> work) {
        calls++;
        running = true;
        try {
            T result = work.run(null);
            if (failWith != null) throw failWith;
            if (deadlocks > 0) {
                deadlocks--;
                throw new DataException(new SQLException(
                        "Deadlock found when trying to get lock; try restarting transaction", "40001", 1213));
            }
            return result;
        } catch (SQLException e) {
            throw new DataException(e);   // what Database.inTransaction does
        } finally {
            running = false;
        }
    }
}
