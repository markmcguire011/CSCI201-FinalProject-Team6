package edu.usc.marketplace.dao;

/**
 * The search box and filters of the Browse page. Every field is optional.
 * Example: new SearchQuery().keyword("lamp").maxPrice(30).page(2)
 */
public class SearchQuery {
    public static final int PAGE_SIZE = 20;

    String keyword, category, condition, sort = "newest";
    Integer minPrice, maxPrice, postedWithinDays;
    Double minRating;
    boolean showSold;
    int page = 1;

    public SearchQuery keyword(String v) { keyword = blankToNull(v); return this; }
    public SearchQuery category(String v) { category = blankToNull(v); return this; }
    public SearchQuery condition(String v) { condition = blankToNull(v); return this; }
    public SearchQuery minPrice(Integer v) { minPrice = v; return this; }
    public SearchQuery maxPrice(Integer v) { maxPrice = v; return this; }
    public SearchQuery minRating(Double v) { minRating = v; return this; }
    public SearchQuery postedWithinDays(Integer v) { postedWithinDays = v; return this; }
    public SearchQuery showSold(boolean v) { showSold = v; return this; }
    /** newest, price_low or price_high. Anything else means newest. */
    public SearchQuery sort(String v) { sort = v == null ? "newest" : v; return this; }
    public SearchQuery page(int v) { page = Math.max(1, v); return this; }

    public int getPage() { return page; }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
