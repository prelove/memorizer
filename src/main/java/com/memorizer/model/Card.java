package com.memorizer.model;

import java.sql.Timestamp;

public class Card {
    public long id;
    public long noteId;
    public Timestamp dueAt;       // nullable for new cards
    public Double intervalDays;   // nullable for new cards
    public double ease;           // default 2.5
    public int reps;
    public int lapses;
    public int status;            // 0 new,1 learning,2 review,3 suspended
    public Timestamp lastReviewAt;

    @Override public String toString() {
        return "Card{id="+id+", noteId="+noteId+", dueAt="+dueAt+", intervalDays="+intervalDays+
                ", ease="+ease+", reps="+reps+", lapses="+lapses+", status="+status+"}";
    }
}
