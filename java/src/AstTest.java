package bsh;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;

import epl.Pad;
import epl.PadException;
import epl.TextState;
import epl.Marker;

public class AstTest extends java.applet.Applet implements Runnable, MouseListener, MouseMotionListener {
    volatile boolean running = false;
    boolean animating = false;
    boolean newflag = false;

    public interface Renderable {
        public void render(graphics.Graphics g, long t);
    };

    Interpreter bsh;
    Renderable bsh_renderable;
    Thread t;
    Image buffer_image;
    Graphics buffer_graphics;
    Rectangle r = new Rectangle(0,0,0,0);
    Font error_font = new Font("Monospaced", Font.PLAIN, 14);

    enum CodeState {
        RUNNING,
        PARSE_ERROR,
        HALTED
    }
    private CodeState code_state;

    String pad_name;
    String wrapped_code;
    String code;
    Pad pad;

    int start_cursor_idx;
    int end_cursor_idx;
    ArrayList<Marker> markers;

    long start_time;

    int err_line;
    String err_str;

    public void init() {
        buffer_image = null;

        bsh = new Interpreter();

        try {
            bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);
        } catch (EvalError e) {
            e.printStackTrace();
        }

        pad_name = getParameter("pad_name");
        if (pad_name == null) {
            pad_name = "testpad";
        }

        URL codeBase = getCodeBase();
        try {
            pad = new Pad(new URL(codeBase.getProtocol(), codeBase.getHost(), codeBase.getPort(), ""), "", null, pad_name);
            pad.connect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PadException e) {
            e.printStackTrace();
        }

        err_str = "Waiting for initial text...";
        code = "";
        wrapped_code = wrapForRender(code);
        code_state = CodeState.HALTED;

        start_cursor_idx = -1;
        end_cursor_idx = -1;

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

    public static String wrapForRender(String code) {
        return "import graphics.Graphics2D;\n\npublic void render(graphics.Graphics2D g, long t) {\n" + code + "\n}\n";
    }

    public void update(Graphics g) {
        if (pad.hasNew() || newflag) {
            newflag = false;
            String new_code;
            TextState new_state = pad.getClientState();
            new_code = new_state.text;
            markers = new_state.markers;

            try {
                start_time = System.currentTimeMillis();
                String new_wrapped_code = wrapForRender(new_code);
                bsh.eval(new_wrapped_code);

                code = new_code;
                wrapped_code = new_wrapped_code;

                code_state = CodeState.RUNNING;

                // parse the render declaration
                /*
                StringReader sr = new StringReader(wrapped_code);
                Parser p = new Parser(sr);

                while (!p.Line()) {
                    SimpleNode node = p.popNode();
                    if (node instanceof BSHMethodDeclaration) {
                        BSHMethodDeclaration meth = (BSHMethodDeclaration) node;

                        if (meth.name.equals("render")) {
                            int children = node.jjtGetNumChildren();

                            for (int i = 0; i < children; i++) {
                                SimpleNode child = node.getChild(i);

                                if (child instanceof BSHBlock) {
                                    BSHBlock b = (BSHBlock) child;

                                    int block_children = b.jjtGetNumChildren();
                                    for (int j = 0; j < block_children; j++) {
                                        System.out.println(b.getChild(j));
                                    }
                                }
                            }
                        }
                    }
                }
                */

            } catch (EvalError e) {
                System.out.println("eval error "+e.toString()+", not accepting new code:\n" + new_code);

                err_str = e.toString();
                err_line = -1;
                code_state = CodeState.PARSE_ERROR;

                // re-eval old code
                try {
                    bsh.eval(code);
                } catch (EvalError e2) {
                    System.out.println("error " + e2.toString() + " reverting to previously ok code " + code);

                    err_str = "!";
                    err_line = -1;
                    code_state = CodeState.HALTED;
                }
            }
        }
        
        paint(g);
    }

    public void paint(Graphics g) {
        if (buffer_image == null || getBounds().width != r.width || getBounds().height != r.height) {

            buffer_image = createImage(getBounds().width, getBounds().height);
            buffer_graphics = buffer_image.getGraphics();

            r = getBounds();
        }

        buffer_graphics.clearRect(0,0,r.width,r.height);

        graphics.Graphics gw = new graphics.Graphics2D(buffer_graphics);

        if (code_state != CodeState.HALTED) {
            try {
                bsh_renderable.render(gw, System.currentTimeMillis()-start_time);
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

        // draw objects

        g.drawImage(buffer_image, 0, 0, this);

    }

    public void mouseClicked(MouseEvent e) {
        try {
            if (start_cursor_idx == -1) {
                String new_circ = "g.setColor(Color.RED);\ng.drawArc(" + e.getX() + "," + e.getY() + ",100,100,0,360);\n";
                int circ_pos = pad.appendText(new_circ);

                start_cursor_idx = pad.registerMarker(circ_pos, true, true);
                end_cursor_idx = pad.registerMarker(circ_pos + new_circ.length(), true, true);

                pad.commitChanges();
            } else {
                int start_pos = markers.get(start_cursor_idx).pos;
                int end_pos = markers.get(end_cursor_idx).pos;
                Pattern p = Pattern.compile("Color\\.([A-Z]+)");
                Matcher m = p.matcher(code.subSequence(start_pos, end_pos));

                if (m.find()) {
                    String color = m.group(1);
                    int color_start = m.start(1) + start_pos;
                    int color_end = m.end(1) + start_pos;

                    if ("RED".equals(color)) {
                        color = "BLUE";
                    } else if ("BLUE".equals(color)) {
                        color = "GREEN";
                    } else if ("GREEN".equals(color)) {
                        color = "RED";
                    } else {
                        color = "BLACK/*nope!*/";
                    }

                    pad.makeChange(color_start, color_end-color_start, color);
                    pad.commitChanges();
                }
            }

        } catch (PadException ex) {
            ex.printStackTrace();
        }

        newflag = true;
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }
}
