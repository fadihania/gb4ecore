package com.fadihania;

record SearchSizeRange(long start, long end) {
    SearchSizeRange {
        if (start < 1) {
            throw new IllegalArgumentException("Search range start must be greater than zero.");
        }
        if (end < start) {
            throw new IllegalArgumentException("Search range end must be greater than or equal to start.");
        }
    }

    boolean canSplit() {
        return start < end;
    }

    SearchSizeRange[] split() {
        if (!canSplit()) {
            throw new IllegalStateException("One-byte search range cannot be split.");
        }
        long midpoint = start + (end - start) / 2;
        return new SearchSizeRange[]{
                new SearchSizeRange(start, midpoint),
                new SearchSizeRange(midpoint + 1, end)
        };
    }

    String query() {
        return "extension:ecore size:" + start + ".." + end;
    }

    static SearchRangeAction actionFor(int totalCount, SearchSizeRange range) {
        if (totalCount < 1000) {
            return SearchRangeAction.PROCESS;
        }
        if (range.canSplit()) {
            return SearchRangeAction.SPLIT;
        }
        return SearchRangeAction.PROCESS_CAPPED;
    }

    @Override
    public String toString() {
        return start + ".." + end;
    }
}
