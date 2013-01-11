import org.json.*;

public class EPLTextState {
    private String text;
    private long rev;
    private long time;

    // Construct from data JSONObject message with collab_client_vars
    public EPLTextState(JSONObject data) throws JSONException {
        JSONObject collab_client_vars = data.getJSONObject("collab_client_vars");

        text = collab_client_vars.getJSONObject("initialAttributedText").getString("text");
        rev = collab_client_vars.getLong("rev");
        time = collab_client_vars.getLong("time");
    }

    // Copy constructor
    public EPLTextState(EPLTextState src) {
        synchronized (src) {
            text = src.getText();
            rev = src.getRev();
            time = src.getTime();
        }
    }

    public synchronized void update(String new_text, long new_time, long new_rev) {
        text = new_text;
        time = new_time;
        rev = new_rev;
    }

    // update from data JSONObject with NEW_CHANGES type
    public void update(JSONObject data) throws JSONException, EPLTalkerException {
        String type = data.getString("type");
        if (!type.equals("NEW_CHANGES")) {
            throw new EPLTalkerException("passed wrong type '"+type+"'");
        }

        String new_text;
        long new_time;
        long new_rev;
        long time_delta = 0;

        try {
            EPLChangeset cs = new EPLChangeset(data.getString("changeset"));

            new_text = cs.applyToText(text);
            new_rev = data.getLong("newRev");
            new_time = data.getLong("currentTime");
            if (!data.isNull("timeDelta")) {
                time_delta = data.getLong("timeDelta");
            }
        } catch (EPLChangesetException e) {
            e.printStackTrace();
            System.out.println(e.toString());

            throw new EPLTalkerException(e.toString());
        }

        update(new_text, new_time, new_rev);
    }

    // accessors
    public String getText() { return text; }
    public long getRev() { return rev; }
    public long getTime() { return time; }
}
