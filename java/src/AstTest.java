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

    AstGlobalMethod render_method;
    int render_end_marker_idx;
    ArrayList<AstGlobalMethod> endpoint_methods;
    ArrayList<AstGlobalMethod> primitive_methods;
    ArrayList<AstGlobalMethod> object_methods;

    Interpreter bsh_interp;
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
    String code;
    String new_code;
    Pad pad;

    int[] line_starts;
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

        render_method = null;
        render_end_marker_idx = -1;
        endpoint_methods = new ArrayList<AstGlobalMethod>();
        primitive_methods = new ArrayList<AstGlobalMethod>();
        object_methods = new ArrayList<AstGlobalMethod>();


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
        return line_starts[t.beginLine-1] + t.beginColumn - 1;
    }

    // inclusive, the last char of the token
    public int tokenEnd(Token t) {
        return line_starts[t.endLine-1] + t.endColumn - 1;
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

                bsh_interp = new Interpreter();

                bsh_interp.eval(new_code);
                bsh_renderable = (Renderable) bsh_interp.getInterface(Renderable.class);

                code = new_code;
                new_code = null;
                line_starts = computeLineStarts(code);

                code_state = CodeState.RUNNING;

                collectMethods();

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
        }
        
        paint(g);
    }

    public void paint(Graphics g) {
        long time = System.currentTimeMillis()-start_time;
        if (buffer_image == null || getBounds().width != r.width || getBounds().height != r.height) {

            buffer_image = createImage(getBounds().width, getBounds().height);
            buffer_graphics = buffer_image.getGraphics();

            r = getBounds();
        }
        Graphics2D buffer_graphics_2d = (Graphics2D)buffer_graphics;

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

        // draw render "holder" with its constituents 
        if (render_method != null) {
            int x = r.width-104;
            int y = 300;

            render_method.renderAsHolder(buffer_graphics_2d, time, x, y, endpoint_methods, primitive_methods, object_methods);
        }

        // draw factories
        {
            int x = 10;
            int y = 300;
            for (AstGlobalMethod m : endpoint_methods) {
                m.renderFactory(buffer_graphics_2d, x, y);
                x += 104;
            }
        }

        // blit!
        g.drawImage(buffer_image, 0, 0, this);

    }

    public void addToRender(AstGlobalMethod m) {
        if (render_method != null) {
            int endPos = tokenEnd(render_method.blockNode.getLastToken());

            try {
                StringBuilder invocation = new StringBuilder();

                invocation.append('\n');
                invocation.append(m.method.name);
                invocation.append('(');

                int params = m.paramsNode.jjtGetNumChildren();
                for (int i = 0; i < params; i++) {
                    BSHFormalParameter p = (BSHFormalParameter)m.paramsNode.getChild(i);
                    SimpleNode type_node = ((BSHType)p.getChild(0)).getTypeNode();
                    String param_name = p.name;

                    if (type_node instanceof BSHAmbiguousName) {
                        String param_type = ((BSHAmbiguousName)type_node).text;
                        String default_val = "null";

                        if (param_type.equals("Color") || param_type.endsWith(".Color")) {
                            default_val = "Color.RED";
                        } else if (param_type.equals("Graphics2D")) {
                            default_val = "g";
                        }

                        invocation.append(default_val);
                    } else if (type_node instanceof BSHPrimitiveType) {
                        Class real_type = ((BSHPrimitiveType)type_node).getType();

                        if (real_type == int.class) {
                            int default_val = 10;

                            // we'll probably want to stir these around a bit
                            if (param_name.equals("x")) {
                                default_val = 100;
                            } else if (param_name.equals("y")) {
                                default_val = 100;
                            } else if (param_name.equals("radius")) {
                                default_val = 50;
                            } else if (param_name.equals("side")) {
                                default_val = 100;
                            } else if (param_name.equals("size")) {
                                default_val = 100;
                            }

                            invocation.append(default_val);
                        }
                    }

                    if (i < params-1) {
                        invocation.append(", ");
                    }
                }

                invocation.append(");\n");

                pad.insertAtMarker(render_end_marker_idx, invocation.toString(), true);

                updatePadState();
            } catch (PadException e) {
                e.printStackTrace();
            }
        }
    }

    public void mouseClicked(MouseEvent e) {
        // draw factories
        {
            int x = 10;
            int y = 300;
            for (AstGlobalMethod m : endpoint_methods) {
                if (e.getX() >= x && e.getX() < x+70 && e.getY() >= y && e.getY() < y+50) {
                    addToRender(m);
                }
                x += 104;
            }
        }

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
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

    void collectMethods() throws ParseException, PadException {
        render_method = null;

        endpoint_methods.clear();
        primitive_methods.clear();
        object_methods.clear();

        // parse to find all function declarations
        StringReader sr = new StringReader(code);
        Parser p = new Parser(sr);

        ArrayList<SimpleNode> decls = new ArrayList<SimpleNode>();
        AstSearch decl_search = new AstSearch(null, BSHMethodDeclaration.class, null);

        while (!p.Line()) {
            SimpleNode node = p.popNode();

            decl_search.searchRecurse(node, decls, 0);
        }

        for (int i = 0; i < decls.size(); i++) {
            BSHMethodDeclaration method = (BSHMethodDeclaration)decls.get(i);
            BSHReturnType returnTypeNode = null;
            BSHFormalParameters paramsNode = null;
            BSHBlock blockNode = null;

            for (int j = 0; j < method.jjtGetNumChildren(); j++) {

                SimpleNode node = method.getChild(j);
                if (node instanceof BSHReturnType) {
                    returnTypeNode = (BSHReturnType)node;
                } else if (node instanceof BSHFormalParameters) {
                    paramsNode = (BSHFormalParameters)node;
                } else if (node instanceof BSHBlock) {
                    blockNode = (BSHBlock)node;
                }
            }

            if (returnTypeNode != null && paramsNode != null && blockNode != null) {
                if (returnTypeNode.isVoid) {
                    // no return, we'll assume that this renders something

                    if (method.name.equals("render")) {
                        int render_end_pos = tokenEnd(blockNode.getLastToken());
                        // may want to check params as well
                        render_method = new AstGlobalMethod(method, returnTypeNode, paramsNode, blockNode);
                        if (render_end_marker_idx == -1) {
                            render_end_marker_idx = pad.registerMarker(render_end_pos, true, true);
                        } else {
                            pad.reregisterMarker(render_end_marker_idx, render_end_pos, true, true);
                        }
                    } else {
                        endpoint_methods.add(new AstGlobalMethod(method, returnTypeNode, paramsNode, blockNode));
                    }
                } else {
                    BSHType return_type = returnTypeNode.getTypeNode();
                    SimpleNode type = return_type.getTypeNode();

                    if (type instanceof BSHPrimitiveType) {
                        BSHPrimitiveType prim_type = (BSHPrimitiveType)type;
                        Class real_type = prim_type.getType();

                        primitive_methods.add(new AstGlobalMethod(method, returnTypeNode, paramsNode, blockNode, real_type));
                    } else if (type instanceof BSHAmbiguousName) {
                        BSHAmbiguousName name = (BSHAmbiguousName)type;

                        object_methods.add(new AstGlobalMethod(method, returnTypeNode, paramsNode, blockNode, name.text));
                    }
                }
            }
        }

        // in particular this is to let us know about the marker
        updatePadState();

    }

}
