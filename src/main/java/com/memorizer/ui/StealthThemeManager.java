package com.memorizer.ui;

import javafx.scene.layout.Region;

/**
 * Manages theme (dark/light) styling for the stealth banner window.
 * Applies CSS classes and background colors based on theme mode.
 */
public class StealthThemeManager {
    
    public enum ThemeMode { DARK, LIGHT }
    
    private ThemeMode currentTheme;
    
    /**
     * Create theme manager with initial theme.
     * @param initialTheme starting theme mode
     */
    public StealthThemeManager(ThemeMode initialTheme) {
        this.currentTheme = initialTheme;
    }
    
    /**
     * Get current theme mode.
     * @return current theme
     */
    public ThemeMode getCurrentTheme() {
        return currentTheme;
    }
    
    /**
     * Set and apply theme to root container.
     * @param theme theme to apply
     * @param root root container
     */
    public void applyTheme(ThemeMode theme, Region root) {
        this.currentTheme = theme;
        
        // Remove old theme classes
        root.getStyleClass().removeAll("drawer-dark", "drawer-light");
        
        // Add new theme class
        if (theme == ThemeMode.DARK) {
            root.getStyleClass().add("drawer-dark");
        } else {
            root.getStyleClass().add("drawer-light");
        }
    }
    
    /**
     * Toggle between dark and light theme.
     * @param root root container to apply theme to
     */
    public void toggleTheme(Region root) {
        ThemeMode newTheme = (currentTheme == ThemeMode.DARK) ? 
            ThemeMode.LIGHT : ThemeMode.DARK;
        applyTheme(newTheme, root);
    }
    
    /**
     * Get background color for current theme.
     * @return CSS background color string
     */
    public String getBackgroundColor() {
        return (currentTheme == ThemeMode.DARK) ? 
            "#2b2b2b" : "#f5f5f5";
    }
    
    /**
     * Get text color for current theme.
     * @return CSS text color string
     */
    public String getTextColor() {
        return (currentTheme == ThemeMode.DARK) ? 
            "#e0e0e0" : "#333333";
    }
    
    /**
     * Get badge background color for current theme.
     * @return CSS background color string
     */
    public String getBadgeBackgroundColor() {
        return (currentTheme == ThemeMode.DARK) ? 
            "#1a1a1a" : "#ffffff";
    }
    
    /**
     * Check if current theme is dark.
     * @return true if dark theme is active
     */
    public boolean isDark() {
        return currentTheme == ThemeMode.DARK;
    }
    
    /**
     * Check if current theme is light.
     * @return true if light theme is active
     */
    public boolean isLight() {
        return currentTheme == ThemeMode.LIGHT;
    }
}
