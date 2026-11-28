package section9;

import edu.usc.marketplace.dao.ListingDAO;
import edu.usc.marketplace.dao.SearchQuery;
import edu.usc.marketplace.model.Listing;
import java.util.List;
import java.util.Set;
import listingsupport.ListingDatabaseFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static edu.usc.marketplace.model.ListingStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "MARKETPLACE_TEST_MYSQL_PORT", matches = "[0-9]+")
class BrowseDatabaseTest extends ListingDatabaseFixture {
    private static List<Integer> ids(ListingDAO.Page page) {
        return page.listings().stream().map(Listing::getId).toList();
    }

    @Test void termsMatchAcrossFieldsAndWildcardCharactersAreLiteral() throws Exception {
        int match = listing("Guide 50%_off", 25, AVAILABLE);
        listing("Guide 50XXoff", 25, PENDING);
        listing("Guide 50%_off", 26, AVAILABLE);
        var q = new SearchQuery().keyword("  GUIDE   csci   50%_off ").minPrice(25).maxPrice(25);
        assertEquals(List.of(match), ids(listings.searchPage(q, null)));
    }

    @Test void statusAndSavedStateAreSpecificToEachViewer() throws Exception {
        int available = listing("A", 10, AVAILABLE);
        int pending = listing("P", 20, PENDING);
        int sold = listing("S", 5, SOLD);
        listing("Hidden", 1, INACTIVE);
        listing("Expired", 1, EXPIRED);
        int removed = listing("Removed", 1, AVAILABLE);
        listings.deleteByAdmin(removed);
        actions.setSaved(buyer, available, true);
        var q = new SearchQuery().sort("price_low");
        assertEquals(List.of(available, pending), ids(listings.searchPage(q, null)));
        q.showSold(true);
        assertEquals(List.of(sold, available, pending), ids(listings.searchPage(q, buyer)));
        assertEquals(Set.of(available), listings.searchPage(q, buyer).savedIds());
        assertTrue(listings.searchPage(q, null).savedIds().isEmpty());
        assertTrue(listings.searchPage(q, 3).savedIds().isEmpty());
    }

    @Test void tiedPricesHaveStablePageBoundariesAndOutOfRangeRequestsClamp() throws Exception {
        for (int i = 1; i <= 21; i++) listing("Book " + i, 10, AVAILABLE);
        execute("UPDATE listings SET created_at='2026-01-01 12:00:00'");
        var q = new SearchQuery().sort("price_high");
        var first = listings.searchPage(q, null);
        assertEquals(21, first.totalCount());
        assertEquals(20, first.listings().size());
        assertEquals(21, first.listings().get(0).getId());
        assertEquals(2, first.listings().get(19).getId());
        assertTrue(first.hasNextPage());
        var last = listings.searchPage(q.page(100), null);
        assertEquals(2, last.pageNumber());
        assertEquals(List.of(1), ids(last));
        assertFalse(last.hasNextPage());
        var empty = listings.searchPage(q.keyword("missing"), null);
        assertEquals(1, empty.pageNumber());
        assertTrue(empty.listings().isEmpty());
    }
}
