import org.json.*;

public class EPLTextState {
    private String text;
    private long rev;

    // Construct from data JSONObject message with collab_client_vars
    public EPLTextState(JSONObject data) throws JSONException {
        JSONObject collab_client_vars = data.getJSONObject("collab_client_vars");

        text = collab_client_vars.getJSONObject("initialAttributedText").getString("text");
        rev = collab_client_vars.getLong("rev");
    }

    // Copy constructor
    public EPLTextState(EPLTextState src) {
        synchronized (src) {
            text = src.getText();
            rev = src.getRev();
        }
    }

    public synchronized void update(String new_text, long new_rev) {
        text = new_text;
        rev = new_rev;
    }

    // update from data JSONObject with NEW_CHANGES type
    public void update(JSONObject data) throws JSONException, EPLTalkerException {
        String type = data.getString("type");
        if (!type.equals("NEW_CHANGES")) {
            throw new EPLTalkerException("passed wrong type '"+type+"'");
        }

        try {
            String new_text;
            long new_time;
            long new_rev;
            long time_delta = 0;
            EPLChangeset cs = new EPLChangeset(data.getString("changeset"));

            new_text = cs.applyToText(text);
            new_rev = data.getLong("newRev");
            new_time = data.getLong("currentTime");
            if (!data.isNull("timeDelta")) {
                time_delta = data.getLong("timeDelta");
            }

            update(new_text, new_rev);
        } catch (EPLChangesetException e) {
            e.printStackTrace();
            System.out.println(e.toString());

            throw new EPLTalkerException(e.toString());
        }
    }

    // update from data JSONObject with ACCEPT_COMMIT type
    public void acceptCommit(JSONObject data, String pending_changes) throws JSONException, EPLTalkerException {
        String type = data.getString("type");
        if (!type.equals("ACCEPT_COMMIT")) {
            throw new EPLTalkerException("passed wrong type '"+type+"'");
        }

        String new_text;
        long new_rev;

        try {
            EPLChangeset cs = new EPLChangeset(pending_changes);

            new_text = cs.applyToText(text);
            new_rev = data.getLong("newRev");

            update(new_text, new_rev);
        } catch (EPLChangesetException e) {
            e.printStackTrace();
            System.out.println(e.toString());
        }
    }

    // accessors
    public String getText() { return text; }
    public long getRev() { return rev; }
}
