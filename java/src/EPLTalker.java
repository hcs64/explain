import io.socket.*;
import org.json.*;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.Socket;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;

// class for talking to an Etherpad Lite server

public class EPLTalker {
    // client states
    enum ClientConnectState {
        NO_CONNECTION,
        GETTING_SESSION_TOKEN,
        GOT_SESSION_TOKEN,
        CONNECTING,
        SENT_CLIENT_READY,
        GOT_VARS            // a normal connected state
    }
    private ClientConnectState client_connect_state;

    private boolean has_new_data;
    private String client_text;

    // following the documentation (Etherpad and EasySync Technical Manual):
    // []A: server_state.text
    //   X: sent_changes
    //   Y: pending_changes
    private EPLTextState server_state;
    private EPLChangeset sent_changes;
    private EPLChangeset pending_changes;

    private volatile JSONObject client_vars = null; // state from the server

    private URL url;
    private String session_token;
    private String token;
    private String client_id;
    private String pad_id;

    private SocketIO socket = null;

    public EPLTalker(URL url, String client_id, String token, String pad_id) {
        this.url = url;

        if (token == null) {
            token = "t." + randomString();
        }

        this.token = token;
        this.client_id = client_id;
        this.pad_id = pad_id;

        client_connect_state = ClientConnectState.NO_CONNECTION;
        has_new_data = false;

        server_state = null;
        sent_changes = null;
        pending_changes = null;
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


    public static String getSessionToken(URL url) throws IOException, MalformedURLException, EPLTalkerException {
        // a really dumb HTTP client so Sun's HttpURLConnection doesn't eat the Set-Cookie
        final String set_cookie = "Set-Cookie: ";
        Socket http_socket = new Socket(url.getHost(), url.getPort());

        OutputStream http_out = http_socket.getOutputStream();
        byte[] http_req = "GET / HTTP/1.0\r\n\r\n".getBytes();
        http_out.write(http_req);
        http_out.flush();

        InputStream http_in_stream = http_socket.getInputStream();
        InputStreamReader http_in_reader = new InputStreamReader(http_in_stream);
        BufferedReader http_bufreader = new BufferedReader(http_in_reader);
        String line;

        while ((line = http_bufreader.readLine()) != null) {
            if (line.startsWith(set_cookie)) {
                String[] entries = line.substring(set_cookie.length()).split("; ");
                for (String entry : entries) {
                    String[] keyval = entry.split("=");
                    if (keyval.length == 2 && keyval[0].equals("express_sid")) {
                        return entry;
                    }
                }
            }
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
        if (client_connect_state != ClientConnectState.NO_CONNECTION) {
            throw new EPLTalkerException("can't connect again");
        }

        socket = new SocketIO(url);

        client_connect_state = ClientConnectState.GETTING_SESSION_TOKEN;
        session_token = getSessionToken(url);
        client_connect_state = ClientConnectState.GOT_SESSION_TOKEN;
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

        client_connect_state = ClientConnectState.CONNECTING;
    }


    private synchronized void sendClientReady() throws JSONException, EPLTalkerException {
        if (client_connect_state != ClientConnectState.CONNECTING) {
            throw new EPLTalkerException("sendClientReady in unexpected state "+client_connect_state);
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

        client_connect_state = ClientConnectState.SENT_CLIENT_READY;
    }

    private synchronized void markDisconnected() {
        client_connect_state = ClientConnectState.NO_CONNECTION;
        notifyAll();
    }

    private synchronized void markNew() {
        has_new_data = true;
        notifyAll();
    }

    public synchronized boolean hasNew() {
        return has_new_data;
    }

    public boolean clientOK() {
        return !(client_connect_state == ClientConnectState.NO_CONNECTION);
    }

    public synchronized boolean waitForNew() {
        while (!has_new_data && clientOK()) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }

        return has_new_data;
    }

    public synchronized boolean waitForNew(long timeout_ms) {
        long start_time = System.currentTimeMillis();

        while (!has_new_data && clientOK() &&
               start_time+timeout_ms < System.currentTimeMillis()) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }

        return has_new_data;
    }

    public synchronized EPLTextState getServerState() {
        // grab a coherent copy of the state
        return new EPLTextState(server_state);
    }

    public synchronized String getClientText() {
        has_new_data = false;

        return client_text;
    }

    public synchronized void makeChange(EPLChangeset changeset) throws EPLChangesetException {
        pending_changes = EPLChangeset.compose(pending_changes, changeset);

        client_text = changeset.applyToText(client_text);
    }

    public synchronized void commitChanges() {
        if (sent_changes.isIdentity() && !pending_changes.isIdentity()) {
            HashMap client_edit_req = new HashMap<String, Object>() {{
                put("component", "pad");
                put("type", "COLLABROOM");
            }};

            HashMap data = new HashMap<String, Object>() {{
                put("type", "USER_CHANGES");
                put("baseRev", server_state.getRev());
                put("changeset", pending_changes);
            }};

            data.put("apool", new HashMap<String, Object>() {{
                    put("numToAttrib", new Object[] {});
                    put("nextNum",0);
                    }});

            client_edit_req.put("data", data);

            socket.send(null, new JSONObject(client_edit_req));

            sent_changes = pending_changes;
            pending_changes = EPLChangeset.identity(sent_changes.newLen);
        }
    }

    private synchronized void setClientVars(JSONObject json) throws JSONException, EPLTalkerException {
        if (client_connect_state != ClientConnectState.SENT_CLIENT_READY) {
            throw new EPLTalkerException("setClientVars in unexpected state "+client_connect_state);
        }

        client_connect_state = ClientConnectState.GOT_VARS;
        client_vars = json.getJSONObject("data");

        server_state = new EPLTextState(client_vars);
        client_text = server_state.getText();

        pending_changes = sent_changes = EPLChangeset.identity(server_state.getText().length());

        markNew();
    }

    // most of the action is in here
    private synchronized void handleCollabRoom(JSONObject json) throws JSONException, EPLTalkerException {
        if (client_connect_state != ClientConnectState.GOT_VARS) {
            throw new EPLTalkerException("handleCollabRoom in unexpected state "+client_connect_state);
        }

        JSONObject data = json.getJSONObject("data");
        String collab_type = data.getString("type");

        if ("NEW_CHANGES".equals(collab_type)) {
            try {
                // This is the heart of the protocol, notation here is from
                // the technical manual and Etherpad Lite's changesettracker.js

                // A' = AB
                server_state.update(data);
                EPLChangeset B = new EPLChangeset(data.getString("changeset"));

                // X' = f(B, X)
                // var oldSubmittedChangeset = submittedChangeset;
                // submittedChangeset = Changeset.follow(c, oldSubmittedChangeset, false, apool);
                EPLChangeset X_prime = EPLChangeset.follow(B, sent_changes, false);

                // c2 = Changeset.follow(oldSubmittedChangeset, c, true, apool);
                EPLChangeset fXB = EPLChangeset.follow(sent_changes, B, true);

                // Y' = f(f(X, B), Y)
                // var preferInsertingAfterUserChanges = true;
                // var oldUserChangeset = userChangeset;
                // userChangeset = Changeset.follow(c2, oldUserChangeset, preferInsertingAfterUserChanges, apool);
                EPLChangeset Y_prime = EPLChangeset.follow(fXB, pending_changes, true);

                // D = f(Y, f(X, B))
                // var postChange = Changeset.follow(oldUserChangeset, c2, !preferInsertingAfterUserChanges, apool);
                EPLChangeset D = EPLChangeset.follow(pending_changes, fXB, false);

                sent_changes = X_prime;
                pending_changes = Y_prime;

                if (!D.isIdentity()) {
                    client_text = D.applyToText(client_text);
                    markNew();
                }
            } catch (EPLChangesetException e) {
                e.printStackTrace();
                throw new EPLTalkerException(e.toString());
            }

        } else if ("ACCEPT_COMMIT".equals(collab_type)) {
            server_state.acceptCommit(data, sent_changes);

            sent_changes = EPLChangeset.identity(server_state.getText().length());

            // the acceptance should not introduce any new data to the client
            //markNew();
        } else if ("USER_NEWINFO".equals(collab_type)) {
            // ignore this for now

        } else if ("USER_LEAVE".equals(collab_type)) {
            // ignore this for now

        } else {
            System.out.println("unsupported COLLABROOM message type = " + collab_type);
        }

    }
}
