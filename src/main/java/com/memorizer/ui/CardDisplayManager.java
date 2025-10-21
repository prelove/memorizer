package com.memorizer.ui;

import com.memorizer.service.StudyService;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Manages card content display and updates for the stealth banner.
 * Handles showing/hiding different card fields based on flip state.
 */
public class CardDisplayManager {
    
    // Card data
    private StudyService.CardView currentCard;
    private long currentCardId = -1;
    private List<String> currentExamples;
    
    // UI components
    private final TextFlow frontTextFlow;
    private final Label backLabel;
    private final Label readingLabel;
    private final Label posLabel;
    private final Label kindLabel;
    private final VBox examplesBox;
    private final Label examplesMini;
    
    /**
     * Create card display manager with UI components.
     */
    public CardDisplayManager(TextFlow frontTextFlow, Label backLabel, Label readingLabel,
                             Label posLabel, Label kindLabel, VBox examplesBox, Label examplesMini) {
        this.frontTextFlow = frontTextFlow;
        this.backLabel = backLabel;
        this.readingLabel = readingLabel;
        this.posLabel = posLabel;
        this.kindLabel = kindLabel;
        this.examplesBox = examplesBox;
        this.examplesMini = examplesMini;
    }
    
    /**
     * Load and display a new card.
     * @param card card view to display
     */
    public void loadCard(StudyService.CardView card) {
        this.currentCard = card;
        this.currentCardId = card != null ? card.getCardId() : -1;
        
        if (card == null) {
            clearDisplay();
            return;
        }
        
        // Normalize examples list (CardView already provides a list)
        this.currentExamples = normalizeExamples(card.getExamples());
        
        // Update card kind label
        // planKind (0=DUE,1=LEECH,2=NEW,3=CHALLENGE); null-safe fallback
        Integer kind = card.getPlanKind();
        updateKindLabel(kind == null ? -1 : kind.intValue());
    }
    
    /**
     * Update display based on flip state.
     * @param flipState current flip state manager
     * @param isNormalMode true for normal mode, false for mini mode
     */
    public void updateDisplay(FlipStateManager flipState, boolean isNormalMode) {
        if (currentCard == null) {
            return;
        }
        
        if (isNormalMode) {
            updateNormalModeDisplay(flipState);
        } else {
            updateMiniModeDisplay(flipState);
        }
    }
    
    /**
     * Update display for normal mode.
     */
    private void updateNormalModeDisplay(FlipStateManager flipState) {
        if (flipState.isShowingFront()) {
            // Show only front
            setFrontText(currentCard.getFront());
            backLabel.setVisible(false);
            readingLabel.setVisible(false);
            posLabel.setVisible(false);
            examplesBox.setVisible(false);
            
        } else if (flipState.isShowingBack()) {
            // Show only back
            setFrontText("");
            backLabel.setText(currentCard.getBack());
            backLabel.setVisible(true);
            readingLabel.setVisible(false);
            posLabel.setVisible(false);
            examplesBox.setVisible(false);
            
        } else if (flipState.isShowingAllDetails()) {
            // Show everything
            setFrontText(currentCard.getFront());
            backLabel.setText(currentCard.getBack());
            backLabel.setVisible(true);
            
            if (hasReading()) {
                readingLabel.setText(currentCard.getReading());
                readingLabel.setVisible(true);
            }
            
            if (hasPos()) {
                posLabel.setText(currentCard.getPos());
                posLabel.setVisible(true);
            }
            
            if (hasExamples()) {
                updateExamplesBox();
                examplesBox.setVisible(true);
            }
        }
    }
    
    /**
     * Update display for mini mode.
     */
    private void updateMiniModeDisplay(FlipStateManager flipState) {
        if (flipState.isShowingFront()) {
            // Show only front
            setFrontText(currentCard.getFront());
            backLabel.setVisible(false);
            examplesMini.setVisible(false);
            
        } else if (flipState.isShowingBack()) {
            // Show only back
            setFrontText(currentCard.getBack());
            backLabel.setVisible(false);
            examplesMini.setVisible(false);
            
        } else if (flipState.isShowingReading()) {
            // Show reading/pos
            String text = buildReadingPosText();
            setFrontText(text);
            backLabel.setVisible(false);
            examplesMini.setVisible(false);
            
        } else if (flipState.isShowingExamples()) {
            // Show first example
            if (hasExamples()) {
                String firstExample = currentExamples.get(0);
                examplesMini.setText(firstExample);
                examplesMini.setVisible(true);
            }
            setFrontText("");
            backLabel.setVisible(false);
        }
    }
    
    /**
     * Set front text in TextFlow.
     */
    private void setFrontText(String text) {
        frontTextFlow.getChildren().clear();
        if (text != null && !text.isEmpty()) {
            Text textNode = new Text(text);
            frontTextFlow.getChildren().add(textNode);
        }
    }
    
    /**
     * Build reading/pos combined text.
     */
    private String buildReadingPosText() {
        StringBuilder sb = new StringBuilder();
        
        if (hasReading()) {
            sb.append(currentCard.getReading());
        }
        
        if (hasPos()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append("[").append(currentCard.getPos()).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Update examples box with all examples.
     */
    private void updateExamplesBox() {
        examplesBox.getChildren().clear();
        
        if (currentExamples != null) {
            for (String example : currentExamples) {
                Label exLabel = new Label(example);
                exLabel.setWrapText(true);
                examplesBox.getChildren().add(exLabel);
            }
        }
    }
    
    /** Normalize examples into a non-null copy for safe rendering. */
    private List<String> normalizeExamples(List<String> examples) {
        if (examples == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(examples);
    }
    
    /**
     * Update card kind label with color coding.
     */
    private void updateKindLabel(int kind) {
        String text;
        String style;
        
        switch (kind) {
            case 0: // DUE
                text = "DUE";
                style = "-fx-text-fill: #4a9eff; -fx-font-weight: bold;";
                break;
            case 1: // LEECH
                text = "LEECH";
                style = "-fx-text-fill: #ff4a4a; -fx-font-weight: bold;";
                break;
            case 2: // NEW
                text = "NEW";
                style = "-fx-text-fill: #4aff88; -fx-font-weight: bold;";
                break;
            case 3: // CHALLENGE
                text = "CHAL";
                style = "-fx-text-fill: #d84aff; -fx-font-weight: bold;";
                break;
            default:
                text = String.valueOf(kind);
                style = "-fx-font-weight: bold;";
        }
        
        kindLabel.setText(text);
        kindLabel.setStyle(style);
    }
    
    /**
     * Clear all display fields.
     */
    private void clearDisplay() {
        setFrontText("(No card available)");
        backLabel.setText("");
        backLabel.setVisible(false);
        readingLabel.setText("");
        readingLabel.setVisible(false);
        posLabel.setText("");
        posLabel.setVisible(false);
        examplesBox.getChildren().clear();
        examplesBox.setVisible(false);
        examplesMini.setText("");
        examplesMini.setVisible(false);
        kindLabel.setText("");
    }
    
    /**
     * Check if current card has reading.
     */
    public boolean hasReading() {
        return currentCard != null && 
               currentCard.getReading() != null && 
               !currentCard.getReading().trim().isEmpty();
    }
    
    /**
     * Check if current card has pos.
     */
    public boolean hasPos() {
        return currentCard != null && 
               currentCard.getPos() != null && 
               !currentCard.getPos().trim().isEmpty();
    }
    
    /**
     * Check if current card has examples.
     */
    public boolean hasExamples() {
        return currentExamples != null && !currentExamples.isEmpty();
    }
    
    /**
     * Get current card ID.
     */
    public long getCurrentCardId() {
        return currentCardId;
    }
    
    /**
     * Get current card view.
     */
    public StudyService.CardView getCurrentCard() {
        return currentCard;
    }
    
    /**
     * Get current examples list.
     */
    public List<String> getCurrentExamples() {
        return currentExamples;
    }
}
