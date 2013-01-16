package epl;

// this class represents a coherent snapshot of text

public class TextState {
    // last known revision from the server
    public final String server_text;
    public final long server_rev;

    // our local state
    public final String client_text;
    public final long client_rev;
    public final Marker[] client_markers;

    public TextState(String server_text, long server_rev, String client_text, long client_rev, Marker[] client_markers) {
        this.server_text = server_text;
        this.server_rev = server_rev;
        this.client_text = client_text;
        this.client_rev = client_rev;
        this.client_markers = client_markers;
    }

}
