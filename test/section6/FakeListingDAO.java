package section6;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.model.Listing;

import java.util.ArrayList;
import java.util.List;

/** A ListingDAO that returns a fixed list instead of reading MySQL, and remembers how findBySeller was called. */
class FakeListingDAO extends ListingDAO {
    final List<Listing> listings = new ArrayList<>();
    Boolean lastIncludeHidden;

    FakeListingDAO() {
        super(null);   // no database: every method the tests use is overridden
    }

    @Override
    public List<Listing> findBySeller(int sellerId, boolean includeHidden) {
        lastIncludeHidden = includeHidden;
        return listings.stream().filter(l -> l.getSellerId() == sellerId).toList();
    }
}
