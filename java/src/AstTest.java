import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import epl.Pad;
import epl.PadException;
import epl.TextState;
import epl.Marker;

import bsh.Interpreter;
import bsh.EvalError;

public class AstTest extends java.applet.Applet implements Runnable, MouseListener, MouseMotionListener, KeyListener {
    volatile boolean running = false;
    boolean animating = false;
    boolean should_send = true;
    boolean should_recv = true;

    public interface Renderable {
        public void render(graphics.Graphics g, long t);
    }

    Interpreter bsh_interp;
    Renderable bsh_renderable;
    Thread t;
    Image buffer_image;
    Graphics buffer_graphics;
    Rectangle r = new Rectangle(0,0,0,0);
    Font error_font = new Font("Monospaced", Font.PLAIN, 14);

    AstUI ui;
    ConcurrentLinkedQueue<AstUI.MouseEvent> input_queue;

    enum CodeState {
        RUNNING,
        PARSE_ERROR,
        HALTED
    }
    private CodeState code_state;

    String pad_name;
    String code;
    String new_code;
    Pad pad;

    Marker[] markers;

    long start_time;

    int err_line;
    String err_str;

    public void init() {
        buffer_image = null;

        bsh_interp = null;
        bsh_renderable = null;

        pad_name = getParameter("pad_name");
        if (pad_name == null) {
            pad_name = "testpad";
        }

        URL codeBase = getCodeBase();
        try {
            pad = new Pad(
                new URL(codeBase.getProtocol(), codeBase.getHost(), codeBase.getPort(), ""),
                "",     // client_id
                null,   // token
                pad_name,
                null    // session_token
            );

            pad.connect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (PadException e) {
            e.printStackTrace();
            return;
        }

        err_str = "Waiting for initial text...";
        code = null;
        new_code = "import graphics.Graphics2D; public void render(Graphics2D g, long t) {}";
        code_state = CodeState.HALTED;

        input_queue = new ConcurrentLinkedQueue<AstUI.MouseEvent>();
        ui = new AstUI();
        ui.init();

        running = true;
        t = new Thread(this);
        t.start();

    }

    public void destroy() {
        running = false;
        try {
            t.join();
        } catch (InterruptedException e) { };
    }

    public void start() {
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
    }

    public void stop() {
        removeMouseListener(this);
    }

    public void run() {
        while (running) {
            repaint();

            try {
                t.sleep(30);
            } catch (InterruptedException e) { }
        }
    }

    public void updatePadState() {
        TextState new_state = pad.getState();

        new_code = new_state.client_text;
        markers = new_state.client_markers;
    }

    public void updateMarkers() {
        TextState new_state = pad.getState();
        markers = new_state.client_markers;
    }

    public void update(Graphics g) {
        // 1. process UI event queue, which will generate edit requests
        ArrayList<AstUI.EditRequest> edit_requests = ui.gatherUIRequests(input_queue);

        // 2. apply requests to pad
        boolean local_edits = (edit_requests.size() > 0);
        for (AstUI.EditRequest r : edit_requests) {
            try {
                r.apply(pad);
            } catch (PadException e) {
                e.printStackTrace();
            }
        }
        edit_requests.clear();

        // 3. update
        try {
            if (pad.update(should_send, should_recv) || local_edits) {
                updatePadState();
            }
        } catch (PadException e) {
            e.printStackTrace();
        }

        if (new_code != null) {
            try {
                // 4. reparse
                start_time = System.currentTimeMillis();

                bsh_interp = new Interpreter();

                bsh_interp.eval(new_code);
                bsh_renderable = (Renderable) bsh_interp.getInterface(Renderable.class);

                code = new_code;
                new_code = null;

                code_state = CodeState.RUNNING;

                // 5. regenerate UI
                ui.generateUI(code, pad, markers);
                updateMarkers();

            } catch (EvalError e) {
                System.out.println("eval error "+e.toString()+", not accepting new code:\n" + new_code);

                err_str = e.toString();
                err_line = -1;
                code_state = CodeState.PARSE_ERROR;
                new_code = null;

                // re-eval old code
                try {
                    bsh_interp = new Interpreter();
                    bsh_interp.eval(code);
                    bsh_renderable = (Renderable) bsh_interp.getInterface(Renderable.class);
                } catch (EvalError e2) {
                    System.out.println("error " + e2.toString() + " reverting to previously ok code " + code);

                    err_str = "!";
                    err_line = -1;
                    code_state = CodeState.HALTED;
                }
            } catch (PadException e) {
                e.printStackTrace();
            }

        } // end "if (new_code != null)"

        // 6. render UI and run user code
        paint(g);
    }

    public void paint(Graphics g) {
        long time = System.currentTimeMillis()-start_time;
        if (buffer_image == null || getBounds().width != r.width || getBounds().height != r.height) {

            buffer_image = createImage(getBounds().width, getBounds().height);
            buffer_graphics = buffer_image.getGraphics();

            r = getBounds();
        }

        buffer_graphics.clearRect(0,0,r.width,r.height);

        graphics.Graphics gw = new graphics.Graphics2D(buffer_graphics);

        if (code_state != CodeState.HALTED) {
            try {
                bsh_renderable.render(gw, time);
            } catch (UndeclaredThrowableException e) {
                System.out.println("Runtime error, HALTING");
                e.printStackTrace();

                Throwable cause = e.getCause();
                if (cause instanceof EvalError) {
                    err_line = ((EvalError)cause).getErrorLineNumber();
                } else {
                    err_line = -1;
                }

                err_str = cause.toString();

                code_state = CodeState.HALTED;
            } catch (Exception e) {
                System.out.println("Runtime error, HALTING");
                e.printStackTrace();

                err_line = -1;
                err_str = "!!!";

                code_state = CodeState.HALTED;
            }
        }

        ui.render(buffer_graphics, r.width, r.height);

        // draw error bar
        if (code_state == CodeState.PARSE_ERROR || code_state == CodeState.HALTED) {

            buffer_graphics.setClip(null);
            buffer_graphics.setFont(error_font);

            buffer_graphics.setColor(Color.BLACK);
            buffer_graphics.fillRect(0,0,r.width,60);
            buffer_graphics.setColor(Color.RED);
            if (err_line != -1) {
                buffer_graphics.drawString("Line: " + String.valueOf(err_line), 25, 25);
            }
            buffer_graphics.drawString(err_str, 25, 45);
        }

        // blit!
        g.drawImage(buffer_image, 0, 0, this);

    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        input_queue.add(new AstUI.MouseEvent(Region.PRESS, e.getX(), e.getY()));
    }

    public void mouseReleased(MouseEvent e) {
        input_queue.add(new AstUI.MouseEvent(Region.RELEASE, e.getX(), e.getY()));
    }

    public void mouseMoved(MouseEvent e) {
        input_queue.add(new AstUI.MouseEvent(Region.MOVE, e.getX(), e.getY()));
    }

    public void mouseDragged(MouseEvent e) {
        input_queue.add(new AstUI.MouseEvent(Region.DRAG, e.getX(), e.getY()));
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

}
