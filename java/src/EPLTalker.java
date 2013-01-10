import io.socket.*;
import org.json.*;
import java.util.HashMap;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;

// class for maintaining a connection to an Etherpad Lite server

public class EPLTalker {
    private String path;
    private String session_token;
    private String token;
    private String client_id;
    private String pad_id;

    SocketIO socket = null;

    public EPLTalker(String path, String client_id, String token, String pad_id) {
        this.path = path;

        if (token == null) {
            token = "t." + randomString();
        }

        this.token = token;
        this.client_id = client_id;
        this.pad_id = pad_id;
    }

    private String randomString() {
        final String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        final int string_length = 20;
        String randomstring = "";

        for (int i = 0; i < string_length; i++)
        {
            int rnum = (int) Math.floor(Math.random() * chars.length());
            randomstring += chars.substring(rnum, rnum + 1);
        }
        return randomstring;
}



    private String getSessionToken() throws IOException, MalformedURLException {
        HttpURLConnection http = (HttpURLConnection) new URL(path).openConnection();

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

        http.disconnect();
        return "";
    }

    public void connect() throws IOException, MalformedURLException {
        socket = new SocketIO(path);

        session_token = getSessionToken();
        socket.addHeader("Cookie", session_token);

        socket.connect(new IOCallback() {
            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {
                try {
                    System.out.println("Server said:" + json.toString(2));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(String data, IOAcknowledge ack) {
                System.out.println("Server said: " + data);
            }

            @Override
            public void onError(SocketIOException socketIOException) {
                System.out.println("an Error occurred");
                socketIOException.printStackTrace();
            }

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established.");

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
            }

            @Override
            public void on(String event, IOAcknowledge ack, Object... args) {
                System.out.println("Server triggered event '" + event + "'");
            }
        });

    }
}
