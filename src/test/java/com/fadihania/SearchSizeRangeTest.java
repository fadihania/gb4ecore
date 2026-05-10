package com.fadihania;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchSizeRangeTest {
    @Test
    void splitsEvenRangeIntoContiguousChildren() {
        SearchSizeRange range = new SearchSizeRange(10241, 20480);

        SearchSizeRange[] children = range.split();

        assertEquals(new SearchSizeRange(10241, 15360), children[0]);
        assertEquals(new SearchSizeRange(15361, 20480), children[1]);
        assertEquals(range.start(), children[0].start());
        assertEquals(children[0].end() + 1, children[1].start());
        assertEquals(range.end(), children[1].end());
    }

    @Test
    void oneByteRangeCannotSplit() {
        SearchSizeRange range = new SearchSizeRange(10241, 10241);

        assertFalse(range.canSplit());
        assertThrows(IllegalStateException.class, range::split);
    }

    @Test
    void splitsOddRangeWithoutGapsOrOverlap() {
        SearchSizeRange range = new SearchSizeRange(1, 5);

        SearchSizeRange[] children = range.split();

        assertEquals(new SearchSizeRange(1, 3), children[0]);
        assertEquals(new SearchSizeRange(4, 5), children[1]);
        assertEquals(3, children[0].end() - children[0].start() + 1);
        assertEquals(2, children[1].end() - children[1].start() + 1);
    }

    @Test
    void buildsGitHubCodeSearchQuery() {
        assertEquals(
                "extension:ecore size:10241..20480",
                new SearchSizeRange(10241, 20480).query());
    }

    @Test
    void rejectsInvalidRanges() {
        assertThrows(IllegalArgumentException.class, () -> new SearchSizeRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SearchSizeRange(2, 1));
    }

    @Test
    void decidesToProcessRangesBelowCap() {
        assertEquals(
                SearchRangeAction.PROCESS,
                SearchSizeRange.actionFor(999, new SearchSizeRange(10241, 20480)));
    }

    @Test
    void decidesToSplitCappedSplittableRanges() {
        assertEquals(
                SearchRangeAction.SPLIT,
                SearchSizeRange.actionFor(1000, new SearchSizeRange(10241, 20480)));
    }

    @Test
    void decidesToProcessCappedOneByteRanges() {
        assertEquals(
                SearchRangeAction.PROCESS_CAPPED,
                SearchSizeRange.actionFor(1000, new SearchSizeRange(10241, 10241)));
    }

    @Test
    void canSplitMultiByteRange() {
        assertTrue(new SearchSizeRange(10241, 10242).canSplit());
    }
}
