package com.memorizer.model;

/**
 * Study content unit consisting of front/back text with optional reading/POS/examples/tags,
 * and an optional deck association. Cards reference notes via {@code noteId}.
 */
public class Note {
    public long id;
    public Long deckId; // nullable
    public String front;
    public String back;
    public String reading;
    public String pos;
    public String examples;
    public String tags;
}
