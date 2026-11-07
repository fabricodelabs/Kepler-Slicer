package com.keplerslicer.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SearchMatchTest {
    @Test public void wordsMatchInAnyOrder() {
        assertTrue(SearchMatch.matches("bambu x1", "Bambu Lab", "Bambu Lab X1 Carbon"));
        assertTrue(SearchMatch.matches("x1 bambu", "Bambu Lab", "Bambu Lab X1 Carbon"));
        assertFalse(SearchMatch.matches("bambu p2s", "Bambu Lab", "Bambu Lab X1 Carbon"));
    }
    @Test public void emptyQueryMatchesEverything() { assertTrue(SearchMatch.matches("  ", "anything")); }
    @Test public void keysAndCaseAndYo() {
        assertTrue(SearchMatch.matches("layer height", "layer_height", null));
        assertTrue(SearchMatch.matches("ЁЛКА", "елка"));
    }
}
