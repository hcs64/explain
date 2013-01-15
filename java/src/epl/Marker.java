package epl;

// structure for tracking the position and behavior of a cursor,
// caret, or similar marker
// immutable to avoid a lot of copying

public class Marker {
    public final int pos;
    public final boolean before;

    // Whether the character this cursor refers to still exists,
    // it may have been shifted to a neighbor
    public boolean valid;

    public Marker(int pos, boolean before, boolean valid) {
        this.pos = pos;
        this.before = before;
        this.valid = valid;
    }
}
