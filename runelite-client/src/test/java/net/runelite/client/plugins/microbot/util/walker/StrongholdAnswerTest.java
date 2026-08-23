package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StrongholdAnswerTest {
    @Test
    public void findAnswerNormalizesBreaksWhitespaceAndPunctuation() {
        assertEquals(
                "Only on the Old School RuneScape website.",
                StrongholdAnswer.findAnswer(
                        "To pass you must answer me this: Where is it safe to use my "
                                + "Old School RuneScape password?<br />"));
    }

    @Test
    public void findAnswerRetainsPartialQuestionMatches() {
        assertEquals(
                "Don't share your information and report the player.",
                StrongholdAnswer.findAnswer("How should I react?"));
    }

    @Test
    public void findAnswerRejectsUnknownAndEmptyQuestions() {
        assertNull(StrongholdAnswer.findAnswer("What is the airspeed velocity of an unladen swallow?"));
        assertNull(StrongholdAnswer.findAnswer(""));
        assertNull(StrongholdAnswer.findAnswer(null));
    }
}
