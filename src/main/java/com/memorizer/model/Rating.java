package com.memorizer.model;

/** User rating mapped to SM-2 like logic. */
public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);
    public final int value;
    Rating(int v){ this.value = v; }
}
