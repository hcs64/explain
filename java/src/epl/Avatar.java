package epl;

// representation of a user's avatar in the document (caret/selection/cursor)
// this is where other users can expect his edits to occur

public class Avatar {
    //
    private final String user_id;
    private String user_name;
    private String color_id;
    // approximate local time when position was last updated, 0 if we've never heard
    private long last_update_time;

    private Marker start_marker, end_marker;

    Avatar(String user_id) {
        this.user_id = user_id;
        this.last_update_time = 0;
        // reversed; assume no selection initially
        start_marker = new Marker(0,false,false);
        end_marker = new Marker(0,true,false);
    }

    void setUserName(String user_name) {
        this.user_name = user_name;
    }

    void setColor(String color_id) {
        this.color_id = color_id;
    }

    void setPos(int start_pos, int end_pos, long time) {
        if (time >= last_update_time) {
            last_update_time = time;

            start_marker = new Marker(start_pos, true, true);
            if (start_pos >= end_pos) {
                end_marker = start_marker;
            } else {
                end_marker = new Marker(end_pos-1, false, true);
            }
        } else {
            // ignore
        }
    }

    void adjustForChangeset(String user_id, Changeset cs, long time) {
        if (user_id.equals(this.user_id)) {
            adjustForOwnChangeset(cs, time);
        } else {
            adjustForOtherChangeset(cs, time);
        }
    }

    private void adjustForOwnChangeset(Changeset cs, long time) {
        // assume that there is now just a caret after the change
        // (at the start of the implicitly retained text)
        if (time >= last_update_time)  {
            start_marker = end_marker = cs.afterThisEdit();
            last_update_time = time;
        }
    }

    private void adjustForOtherChangeset(Changeset cs, long time) {
        if (time >= last_update_time ) {
            // assume normal marker translation as caused by another
            start_marker = cs.translateMarker(start_marker);
            end_marker = cs.translateMarker(end_marker);
            last_update_time = time;
        }
    }

    public long getTime() {
        return last_update_time;
    }

    public String getUserName() {
        return user_name;
    }

    public String getUserId() {
        return user_id;
    }

    public String getColor() {
        return color_id;
    }

    public Marker getStartMarker() {
        return start_marker;
    }

    public Marker getEndMarker() {
        return end_marker;
    }

}
