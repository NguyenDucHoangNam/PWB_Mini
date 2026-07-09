package com.pwb.backend.iam.internal.helper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PiiScrubberTest {

    @Test
    void maskEmailPreservesDomainAndFirstLastChar() {
        assertEquals("a***e@gmail.com", PiiScrubber.maskEmail("alice@gmail.com"));
    }

    @Test
    void maskEmailShortLocalUsesDoubleStar() {
        assertEquals("**@gmail.com", PiiScrubber.maskEmail("a@gmail.com"));
    }

    @Test
    void maskEmailNullOrBlankReturnsNone() {
        assertEquals("<none>", PiiScrubber.maskEmail(null));
        assertEquals("<none>", PiiScrubber.maskEmail(""));
    }

    @Test
    void maskEmailInvalidInputReturnsStar() {
        assertEquals("***", PiiScrubber.maskEmail("@nope"));
    }

    @Test
    void maskPhoneShowsLastFourDigits() {
        assertEquals("***1234", PiiScrubber.maskPhone("+1-555-123-1234"));
    }

    @Test
    void maskPhoneShortReturnsTripleStar() {
        assertEquals("<none>", PiiScrubber.maskPhone(""));
        assertEquals("***", PiiScrubber.maskPhone("12"));
    }

    @Test
    void userRefRedactsUuid() {
        assertEquals("abcd...abcd", PiiScrubber.userRef("abcd1234-1234-1234-1234-5678efabcd"));
    }

    @Test
    void userRefShortValueReturnsStars() {
        assertEquals("***", PiiScrubber.userRef("abc"));
        assertEquals("<none>", PiiScrubber.userRef(null));
    }

    @Test
    void userRefDifferentInputsProduceDifferentOutput() {
        assertNotEquals(PiiScrubber.userRef("user-aaaa1111"), PiiScrubber.userRef("user-bbbb2222"));
    }
}