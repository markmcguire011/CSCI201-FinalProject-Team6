package edu.usc.marketplace.servlet;

import edu.usc.marketplace.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SearchValidationTest {
    private HttpServletRequest query(Map<String,String> values) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getParameter")) return values.get(args[0]);
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    @ParameterizedTest
    @CsvSource({"minPrice,-1","maxPrice,10001","minPrice,2.5","minRating,0","minRating,6",
            "days,2","page,0","page,2147483648","category,Unknown","sort,oldest"})
    void rejectsInvalidFieldBeforeDatabaseAccess(String field, String value) {
        var error = assertThrows(ValidationException.class, () -> ListingBrowse.parse(query(Map.of(field, value))));
        assertTrue(error.getFieldErrors().containsKey(field));
    }

    @Test void priceRangeBoundaries() {
        assertDoesNotThrow(() -> ListingBrowse.parse(query(Map.of("minPrice","0","maxPrice","10000","days","1"))));
        assertDoesNotThrow(() -> ListingBrowse.parse(query(Map.of("minPrice","25","maxPrice","25"))));
        assertThrows(ValidationException.class, () -> ListingBrowse.parse(query(Map.of("minPrice","26","maxPrice","25"))));
    }
}
