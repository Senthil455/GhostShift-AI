package ai.ghostshift.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {

    @Test
    void truncatesWithAnEllipsis() {
        assertEquals("short", Text.truncate(" short ", 20));
        assertEquals("abc…", Text.truncate("abcdef", 3));
    }

    @Test
    void stripsUnsafeFileNameCharacters() {
        String safe = Text.safeFileName("invoice: 2026\nfinal?.pdf");

        assertTrue(safe.contains("invoice- 2026"));
        assertTrue(safe.endsWith(".pdf"));
        assertTrue(!safe.contains("?"));
    }

    @Test
    void neverReturnsPathMarkersAsNames() {
        assertEquals("file", Text.safeFileName(".."));
        assertEquals("file", Text.safeFileName("   "));
    }

    @Test
    void extractsExtensionsCaseInsensitively() {
        assertEquals(".pdf", Text.extension("Report.PDF"));
        assertEquals("", Text.extension("README"));
        assertEquals("", Text.extension(".env"));
    }

    @Test
    void normalizesLogSignaturesForGrouping() {
        String a = Text.normalizeForSignature("2026-03-10T09:15:02Z ERROR request 1043 failed at C:\\temp\\a.log");
        String b = Text.normalizeForSignature("2026-03-11T11:00:00Z ERROR request 9999 failed at /tmp/b.log");

        assertEquals(a, b);
    }

    @Test
    void hashesDeterministically() {
        assertEquals(Text.sha256("ghostshift"), Text.sha256("ghostshift"));
        assertTrue(Text.sha256("ghostshift").matches("[0-9a-f]{64}"));
    }
}
