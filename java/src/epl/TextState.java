package epl;

import java.util.ArrayList;

// this class represents a coherent snapshot of changing state

public class TextState {
    public final String text;

    // -1 if we aren't on a real revision
    public final long rev;
    // last known revision from the server
    public final long server_rev;

    public final ArrayList<Marker> markers;

    public TextState(String text, long rev, long server_rev, ArrayList<Marker> markers) {
        this.text = text;
        this.rev = rev;
        this.server_rev = server_rev;
        this.markers = markers;
    }

}
