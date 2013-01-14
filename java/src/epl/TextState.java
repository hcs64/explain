package epl;

import org.json.*;

// this class is not synchronized at all

class TextState {
    private String text;
    private long rev;

    // Construct from data JSONObject message with collab_client_vars
    public TextState(JSONObject data) throws JSONException {
        JSONObject collab_client_vars = data.getJSONObject("collab_client_vars");

        text = collab_client_vars.getJSONObject("initialAttributedText").getString("text");
        rev = collab_client_vars.getLong("rev");
    }

    // Copy constructor
    public TextState(TextState src) {
        synchronized (src) {
            text = src.getText();
            rev = src.getRev();
        }
    }

    public synchronized void update(String new_text, long new_rev) {
        if (new_rev > rev) {
            text = new_text;
            rev = new_rev;
        }
    }

    // update from data JSONObject with NEW_CHANGES type
    public void update(JSONObject data) throws PadException {
        String new_text;
        long new_time;
        long new_rev;
        long time_delta = 0;
        String cs_str;
        Changeset cs;

        try {
            cs_str = data.getString("changeset");

            new_rev = data.getLong("newRev");
            new_time = data.getLong("currentTime");
            if (!data.isNull("timeDelta")) {
                time_delta = data.getLong("timeDelta");
            }
        } catch (JSONException e) {
            throw new PadException("error updating from NEW_CHANGES", e);
        }

        try {
            cs = new Changeset(cs_str);
            new_text = cs.applyToText(text);
            update(new_text, new_rev);
        } catch (ChangesetException e) {
            throw new PadException("update of TextState fails", e);
        }
    }

    // update from data JSONObject with ACCEPT_COMMIT type
    public void acceptCommit(JSONObject data, Changeset confirmed_changes) throws PadException {
        String new_text;
        long new_rev;

        try {
            new_rev = data.getLong("newRev");
        } catch (JSONException e) {
            throw new PadException("failed getting ACCEPT_COMMIT's newRev", e);
        }

        try {
            new_text = confirmed_changes.applyToText(text);

        } catch (ChangesetException e) {
            throw new PadException("failed applying confirmed changes on ACCEPT_COMMIT", e);
        }

        update(new_text, new_rev);
    }

    // accessors
    public String getText() { return text; }
    public long getRev() { return rev; }
}
