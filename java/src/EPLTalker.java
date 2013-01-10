import io.socket.*;
import org.json.*;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;

// class for talking to an Etherpad Lite server

public class EPLTalker {
    // client states
    static final int NO_CONNECTION          = 1;
    static final int GETTING_SESSION_TOKEN  = 2;
    static final int GOT_SESSION_TOKEN      = 3;
    static final int CONNECTING             = 4;
    static final int SENT_CLIENT_READY      = 5;
    static final int GOT_VARS               = 6;    // a normal connected state

    private volatile int client_state;
    private volatile boolean new_data;
    private volatile String server_text;
    private volatile JSONObject client_vars = null; // state from the server

    private String path;
    private String session_token;
    private String token;
    private String client_id;
    private String pad_id;


    private SocketIO socket = null;

    public EPLTalker(String path, String client_id, String token, String pad_id) {
        this.path = path;

        if (token == null) {
            token = "t." + randomString();
        }

        this.token = token;
        this.client_id = client_id;
        this.pad_id = pad_id;

        this.client_state = NO_CONNECTION;
        this.new_data = false;
    }

    // adapted from Etherpad Lite's JS
    public static String randomString() {
        final String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        final int string_length = 20;
        StringBuilder randomstring = new StringBuilder(string_length);

        for (int i = 0; i < string_length; i++)
        {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring.append(chars.charAt(rnum));
        }
        return randomstring.toString();
    }


    public static String getSessionToken(String path) throws IOException, MalformedURLException, EPLTalkerException {
        HttpURLConnection http = (HttpURLConnection) new URL(path).openConnection();

        try {
            String field = null;

            for (int i = 0; (field = http.getHeaderField(i)) != null; i++) {
                String key = http.getHeaderFieldKey(i);
                if (key != null && key.equals("Set-Cookie")) {
                    String[] entries = field.split(";");
                    for (String entry : entries) {
                        String[] keyval = entry.split("=");
                        if (keyval.length == 2 && keyval[0].equals("express_sid")) {
                            return entry;
                        }
                    }
                }
            }
        }
        finally {
            http.disconnect();
        }

        throw new EPLTalkerException("no express_sid found");
    }

    private void handleIncomingMessage(JSONObject json) throws JSONException, EPLTalkerException {
        String type = json.getString("type");

        if ("CLIENT_VARS".equals(type)) {
            setClientVars(json);
        } else if ("COLLABROOM".equals(type)) {
            handleCollabRoom(json);
        } else {
            // unhandled message type
            System.out.print("unknown message type: " + type + ", keys: ");
            for (Iterator i = json.keys(); i.hasNext(); ) {
                String k = (String) i.next();
                System.out.print("'"+k+"' ");
            }
            System.out.println();
        }
    }


    public synchronized void connect() throws IOException, MalformedURLException, EPLTalkerException {
        if (client_state != NO_CONNECTION) {
            throw new EPLTalkerException("can't connect again");
        }

        socket = new SocketIO(path);

        client_state = GETTING_SESSION_TOKEN;
        session_token = getSessionToken(path);
        client_state = GOT_SESSION_TOKEN;
        socket.addHeader("Cookie", session_token);

        socket.connect(new IOCallback() {
            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
                try {
                    handleIncomingMessage(json);
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (EPLTalkerException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
                System.out.println("Server sent string: " + data);
            }

            @Override
            public void onError(SocketIOException socketIOException) {
                System.out.println("an Error occurred");
                socketIOException.printStackTrace();
            }

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
                markDisconnected();
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established.");

                try {
                    sendClientReady();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (EPLTalkerException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {
                System.out.println("Server triggered event '" + event + "'");
            }
        });

        client_state = CONNECTING;
    }


    private synchronized void sendClientReady() throws JSONException, EPLTalkerException {
        if (client_state != CONNECTING) {
            throw new EPLTalkerException("sendClientReady in unexpected state "+client_state);
        }

        HashMap client_ready_req = new HashMap<String, Object>() {{
            put("component", "pad");
            put("type", "CLIENT_READY");
            put("padId", pad_id);
            put("sessionID", null);
            put("token", token);
            put("password", null);
            put("protocolVersion", 2);
        }};

        JSONObject client_ready_json = new JSONObject(client_ready_req);

        socket.send(client_ready_json);

        client_state = SENT_CLIENT_READY;
    }

    private synchronized void markDisconnected() {
        client_state = NO_CONNECTION;
        notifyAll();
    }

    private synchronized void markNew() {
        new_data = true;
        notifyAll();
    }

    public synchronized boolean hasNew() {
        return new_data;
    }

    public boolean clientOK() {
        int state = client_state;
        return !(state == NO_CONNECTION);
    }

    public synchronized boolean waitForNew() {
        while (!new_data && clientOK()) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }

        return new_data;
    }

    public synchronized boolean waitForNew(long timeout_ms) {
        long start_time = System.currentTimeMillis();

        while (!new_data && clientOK() &&
               start_time+timeout_ms < System.currentTimeMillis()) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }

        return new_data;
    }

    public synchronized String getText() {
        new_data = false;

        return server_text;
    }

    private void setClientVars(JSONObject json) throws JSONException, EPLTalkerException {
        if (client_state != SENT_CLIENT_READY) {
            throw new EPLTalkerException("setClientVars in unexpected state "+client_state);
        }

        client_state = GOT_VARS;
        client_vars = json.getJSONObject("data");

        JSONObject collab_client_vars = client_vars.getJSONObject("collab_client_vars");

        JSONObject initial_attributed_text = collab_client_vars.getJSONObject("initialAttributedText");

        server_text = initial_attributed_text.getString("text");

        markNew();
    }

    // most of the action is in here
    private synchronized void handleCollabRoom(JSONObject json) throws JSONException, EPLTalkerException {
        if (client_state != GOT_VARS) {
            throw new EPLTalkerException("handleCollabRoom in unexpected state "+client_state);
        }

        JSONObject data = json.getJSONObject("data");
        String collab_type = data.getString("type");


        if ("NEW_CHANGES".equals(collab_type)) {
            EPLChangeset cs;
            
            try {
                cs  = new EPLChangeset(data.getString("changeset"));
            } catch (EPLChangesetException e) {
                System.out.println(e.toString());
            }

            markNew();
        } else if ("USER_NEWINFO".equals(collab_type)) {
            // ignore this for now

        } else if ("USER_LEAVE".equals(collab_type)) {
            // ignore this for now

        } else {
            System.out.println("unsupported COLLABROOM message type = " + collab_type);
        }

    }
}
