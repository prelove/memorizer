package com.memorizer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FlipStateManagerTest {

    @Test
    void initialStateShouldBeFront() {
        FlipStateManager fsm = new FlipStateManager();
        assertEquals(0, fsm.getFlipCount());
        assertTrue(fsm.isShowingFront());
        assertFalse(fsm.isShowingBack());
        assertFalse(fsm.isShowingAllDetails());
    }

    @Test
    void normalModeThreeStateCycle() {
        FlipStateManager fsm = new FlipStateManager();
        // State 0: front
        assertEquals(0, fsm.getFlipCount());

        fsm.advance(true); // → state 1: back
        assertEquals(1, fsm.getFlipCount());
        assertTrue(fsm.isShowingBack());

        fsm.advance(true); // → state 2: all details
        assertEquals(2, fsm.getFlipCount());
        assertTrue(fsm.isShowingAllDetails());
        assertTrue(fsm.isShowingReading());

        fsm.advance(true); // → wraps back to state 0
        assertEquals(0, fsm.getFlipCount());
        assertTrue(fsm.isShowingFront());
    }

    @Test
    void miniModeFourStateCycle() {
        FlipStateManager fsm = new FlipStateManager();
        // State 0: front
        assertEquals(0, fsm.getFlipCount());

        fsm.advance(false); // → state 1: back
        assertEquals(1, fsm.getFlipCount());

        fsm.advance(false); // → state 2: reading/pos
        assertEquals(2, fsm.getFlipCount());
        assertTrue(fsm.isShowingReading());

        fsm.advance(false); // → state 3: examples
        assertEquals(3, fsm.getFlipCount());
        assertTrue(fsm.isShowingExamples());

        fsm.advance(false); // → wraps back to state 0
        assertEquals(0, fsm.getFlipCount());
    }

    @Test
    void resetReturnToFront() {
        FlipStateManager fsm = new FlipStateManager();
        fsm.advance(true);
        fsm.advance(true);
        assertEquals(2, fsm.getFlipCount());

        fsm.reset();
        assertEquals(0, fsm.getFlipCount());
        assertFalse(fsm.isReadingShown());
    }

    @Test
    void normalModeDoesNotReachState3() {
        FlipStateManager fsm = new FlipStateManager();
        // Advance through full normal cycle and confirm it never hits state 3
        for (int i = 0; i < 9; i++) {
            assertTrue(fsm.getFlipCount() < 3, "Normal mode should not exceed state 2");
            fsm.advance(true);
        }
    }

    @Test
    void readingShownFlagIsIndependent() {
        FlipStateManager fsm = new FlipStateManager();
        assertFalse(fsm.isReadingShown());
        fsm.setReadingShown(true);
        assertTrue(fsm.isReadingShown());
        fsm.reset();
        assertFalse(fsm.isReadingShown());
    }
}
