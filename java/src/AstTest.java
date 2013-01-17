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
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.ArrayList;

import epl.Pad;
import epl.PadException;
import epl.TextState;
import epl.Marker;

public class AstTest extends java.applet.Applet implements Runnable, MouseListener, MouseMotionListener, KeyListener {
    volatile boolean running = false;
    boolean animating = false;
    boolean should_send = true;
    boolean should_recv = true;

    public interface Renderable {
        public void render(graphics.Graphics g, long t);
    }

    class Circle {
        private int x;
        private int y;
        private int start_cursor_idx;
        private int end_cursor_idx;

        public final Pattern p = Pattern.compile("g.setColor\\(Color\\.[A-Z]+\\);");

        public Circle(int x, int y) throws PadException {
            this.x = x;
            this.y = y;

            String new_circ = "g.setColor(Color.RED);\ng.drawArc(" + x + "," + y + ",100,100,0,360);";
            pad.appendText("\n");
            start_cursor_idx = pad.appendTextAndMark(new_circ);
            end_cursor_idx = start_cursor_idx + 1;
            updatePadState();
        }

        public Matcher matcher(CharSequence cs) {
            return p.matcher(cs);
        }

        public boolean isValid() {
            if (markers[start_cursor_idx].valid && markers[end_cursor_idx].valid) {
                return true;
            }

            return false;
        }

        public void modify() throws PadException {
            if (isValid()) {
                CharSequence cs = code.subSequence(markers[start_cursor_idx].pos, markers[end_cursor_idx].pos+1);
                Matcher m = matcher(cs);
                if (m.find()) {
                    x++; y++;
                    String new_circ = "g.setColor(Color.RED);\ng.drawArc(" + x + "," + y + ",100,100,0,360);";
                    pad.replaceBetweenMarkers(start_cursor_idx, end_cursor_idx, new_circ);
                    updatePadState();
                } else {
                    System.out.println("no match");
                }
            } else {
                System.out.println("not valid");
            }
        }

        public void render(Graphics g) {
            g.setColor(Color.BLACK);
            g.drawRect(x,y,100,100);
        }
    }

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
    String new_code;
    Pad pad;

    LinkedList<Circle> known_circles;
    int[] line_starts;
    Marker[] markers;

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
        wrapped_code = null;
        new_code = "";
        code_state = CodeState.HALTED;

        known_circles = new LinkedList<Circle>();
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

    static final String RENDER_WRAP_PREFIX = "import graphics.Graphics2D;\n\npublic void render(graphics.Graphics2D g, long t) {\n";
    static final String RENDER_WRAP_POSTFIX = "\n}\n";
    public static String wrapForRender(String code) {
        return RENDER_WRAP_PREFIX + code + RENDER_WRAP_POSTFIX;
    }

    public int unwrapPosition(int pos) {
        int adj_pos = pos - RENDER_WRAP_PREFIX.length();

        if (adj_pos < 0) return 0;
        if (adj_pos >= code.length()) return code.length()-1;
        return adj_pos;
    }

    public static int[] computeLineStarts(String s) {
        ArrayList<Integer> a = new ArrayList<Integer>();

        int pos = -1;
        do {
            pos++;
            a.add(pos);
        } while ((pos = s.indexOf('\n', pos)) >= 0);

        int[] array = new int[a.size()];
        for (int i = 0; i < a.size(); i++) {
            array[i] = a.get(i);
        }

        return array;
    }

    public int tokenStart(Token t) {
        return unwrapPosition(line_starts[t.beginLine-1] + t.beginColumn - 1);
    }

    // inclusive, the last char of the token
    public int tokenEnd(Token t) {
        return unwrapPosition(line_starts[t.endLine-1] + t.endColumn - 1);
    }

    public void updatePadState() {
        TextState new_state = pad.getState();
        new_code = new_state.client_text;
        markers = new_state.client_markers;
    }

    public void update(Graphics g) {
        try {
            if (pad.update(should_send, should_recv)) {
                updatePadState();
            }
        } catch (PadException e) {
            e.printStackTrace();
        }

        if (new_code != null) {
            try {
                start_time = System.currentTimeMillis();
                String new_wrapped_code = wrapForRender(new_code);
                bsh.eval(new_wrapped_code);

                code = new_code;
                wrapped_code = new_wrapped_code;
                new_code = null;
                new_wrapped_code = null;
                line_starts = computeLineStarts(wrapped_code);

                code_state = CodeState.RUNNING;

            } catch (EvalError e) {
                System.out.println("eval error "+e.toString()+", not accepting new code:\n" + new_code);

                err_str = e.toString();
                err_line = -1;
                code_state = CodeState.PARSE_ERROR;
                new_code = null;

                // re-eval old code
                try {
                    bsh.eval(wrapped_code);
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
        /*
        if (known_circles.size() == 0) {
        try {
            known_circles.add(new Circle(e.getX(), e.getY()));
        } catch (PadException ex) {
            ex.printStackTrace();
        }
        } else {

        try {
            for (ListIterator<Circle> i = known_circles.listIterator(); i.hasNext(); ) {
                Circle c = i.next();
                c.modify();
            }
        } catch (PadException ex) {
            ex.printStackTrace();
        }
        }
        */
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

    public void keyPressed(KeyEvent e) {
        try {
            if (e.getKeyCode() == KeyEvent.VK_A) {
                // parse the render declaration
                StringReader sr = new StringReader(wrapped_code);
                Parser p = new Parser(sr);
                AstSearch pri_expr = new AstSearch(null, BSHPrimaryExpression.class, null);
                AstSearch arc_call = new AstSearch(null, BSHMethodInvocation.class,
                    new AstSearch[] {
                        new AstSearch("AmbiguousName: g.drawArc", BSHAmbiguousName.class, null),
                        new AstSearch(null, BSHArguments.class,
                            new AstSearch[] { pri_expr, pri_expr, pri_expr, pri_expr, pri_expr, pri_expr })
                    }
                );

                while (!p.Line()) {
                    SimpleNode node = p.popNode();

                    if (node instanceof BSHMethodDeclaration) {
                        BSHMethodDeclaration meth = (BSHMethodDeclaration) node;

                        if (meth.name.equals("render")) {
                            SimpleNode[] nodes = arc_call.search(meth, 3);

                            for (int i = 0; i < nodes.length; i++) {
                                SimpleNode circler = nodes[i];

                                System.out.println(code.substring(tokenStart(circler.getFirstToken()), tokenEnd(circler.getLastToken())+1));
                            }
                        }
                    }
                }

            }
        } catch (ParseException ex) {
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }
}
