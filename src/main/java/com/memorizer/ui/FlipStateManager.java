package com.memorizer.ui;

/**
 * Manages flip state progression for card display.
 * 
 * Flip cycles:
 * - Normal mode: Front -> Back -> Front+Back+Details -> Front (repeats)
 * - Mini mode: Front -> Back -> Reading/Pos -> Examples -> Front (repeats)
 */
public class FlipStateManager {
    private int flipPressCount = 0;
    private boolean readingShown = false;
    
    /**
     * Advance to next flip state.
     * @param isNormalMode true for normal mode, false for mini mode
     */
    public void advance(boolean isNormalMode) {
        flipPressCount++;
        
        // Normal mode: 3 states (0=Front, 1=Back, 2=All details)
        // Mini mode: 4 states (0=Front, 1=Back, 2=Reading/Pos, 3=Examples)
        int maxStates = isNormalMode ? 3 : 4;
        
        if (flipPressCount >= maxStates) {
            flipPressCount = 0;
        }
    }
    
    /**
     * Reset flip state to front.
     */
    public void reset() {
        flipPressCount = 0;
        readingShown = false;
    }
    
    /**
     * Get current flip press count.
     * @return flip state index
     */
    public int getFlipCount() {
        return flipPressCount;
    }
    
    /**
     * Check if showing front side.
     * @return true if on front state
     */
    public boolean isShowingFront() {
        return flipPressCount == 0;
    }
    
    /**
     * Check if showing back side.
     * @return true if on back state
     */
    public boolean isShowingBack() {
        return flipPressCount == 1;
    }
    
    /**
     * Check if showing all details (normal mode only).
     * @return true if on details state
     */
    public boolean isShowingAllDetails() {
        return flipPressCount == 2;
    }
    
    /**
     * Check if showing reading/pos (mini mode only).
     * @return true if on reading state
     */
    public boolean isShowingReading() {
        return flipPressCount == 2;
    }
    
    /**
     * Check if showing examples (mini mode only).
     * @return true if on examples state
     */
    public boolean isShowingExamples() {
        return flipPressCount == 3;
    }
    
    /**
     * Set reading shown flag.
     * @param shown reading visibility state
     */
    public void setReadingShown(boolean shown) {
        this.readingShown = shown;
    }
    
    /**
     * Check if reading has been shown.
     * @return true if reading was displayed
     */
    public boolean isReadingShown() {
        return readingShown;
    }
}
