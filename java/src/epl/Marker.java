package epl;

// structure for tracking the position and behavior of a cursor,
// caret, or similar marker
// immutable to avoid defensive copying

public class Marker {
    public final int pos;

    // true if the marker is considered to be before the character as index pos, as with the
    // B in A[BC,
    // false if after, as with the B in AB]C
    public final boolean before;

    // Whether the character this cursor refers to still exists;
    // it may have been shifted to a neighbor
    public boolean valid;

    public Marker(int pos, boolean before, boolean valid) {
        this.pos = pos;
        this.before = before;
        this.valid = valid;
    }
}
