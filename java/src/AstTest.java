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
    ArrayList<AstGlobalMethod> endpoint_methods;
    ArrayList<AstGlobalMethod> primitive_methods;
    ArrayList<AstGlobalMethod> object_methods;

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
        new_code = "";
        code_state = CodeState.HALTED;

        render_method = null;
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

    static final String RENDER_WRAP_PREFIX = "import graphics.Graphics2D;\n\npublic void render(graphics.Graphics2D g, long t) {\n";
    static final String RENDER_WRAP_POSTFIX = "\n}\n";
    public static String wrapForRender(String code) {
        return RENDER_WRAP_PREFIX + code + RENDER_WRAP_POSTFIX;
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
                bsh.eval(new_code);

                code = new_code;
                new_code = null;
                line_starts = computeLineStarts(code);

                code_state = CodeState.RUNNING;

            } catch (EvalError e) {
                System.out.println("eval error "+e.toString()+", not accepting new code:\n" + new_code);

                err_str = e.toString();
                err_line = -1;
                code_state = CodeState.PARSE_ERROR;
                new_code = null;

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
            int x = r.width-100;
            int y = 300;

            int block_elements = render_method.blockNode.jjtGetNumChildren();
            for (int i = 0; i < block_elements; i++) {
                SimpleNode node = render_method.blockNode.getChild(i);
                if ((node instanceof BSHPrimaryExpression) && (node.getChild(0) instanceof BSHMethodInvocation)) {
                    BSHMethodInvocation method_invocation = (BSHMethodInvocation)node.getChild(0);

                    String name = method_invocation.getNameNode().text;
                    BSHArguments args = method_invocation.getArgsNode();

                    // now we want to be able to find and render that method
                    // search backwards in case it is multiply declared...
                    for (int j = endpoint_methods.size()-1; j >= 0; j--) {
                        AstGlobalMethod meth = endpoint_methods.get(j);
                        if (meth.method.name.equals(name)) {
                            meth.renderNode(buffer_graphics_2d, time, x, y);
                            y += 100;
                        }
                    }
                }
            }
        }

        // draw factories
        {
            int x = 10;
            int y = 300;
            for (AstGlobalMethod m : endpoint_methods) {
                m.renderFactory(buffer_graphics_2d, x, y);
                x += 100;
            }
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
    }

    public void mouseReleased(MouseEvent e) {
    }

    public void mouseMoved(MouseEvent e) {
    }

    public void mouseDragged(MouseEvent e) {
    }

    public void keyPressed(KeyEvent e) {
        try {
            switch (e.getKeyCode()) {
            case KeyEvent.VK_A:
                collectMethods();
                break;

            case KeyEvent.VK_C:
                break;
            }
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
    }

    public void keyReleased(KeyEvent e) {
    }

    public void keyTyped(KeyEvent e) {
    }

    void collectMethods() throws ParseException {
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
                    System.out.println("void " + method.name + "(" + paramsNode.toString() + ")");

                    if (method.name.equals("render")) {
                        // may want to check params as well
                        render_method = new AstGlobalMethod(method, returnTypeNode, paramsNode, blockNode);
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

/*
        // parse the render declaration
        StringReader sr = new StringReader(code);
        Parser p = new Parser(sr);
        AstSearch pri_expr = new AstSearch(null, BSHPrimaryExpression.class, null);
        AstSearch fill_arc_call = new AstSearch(null, BSHMethodInvocation.class,
                new AstSearch[] {
                new AstSearch("g.fillArc", BSHAmbiguousName.class, null),
                new AstSearch(null, BSHArguments.class,
                    new AstSearch[] { pri_expr, pri_expr, pri_expr, pri_expr, pri_expr, pri_expr })
                }
                );
        AstSearch render_decl = new AstSearch("render", BSHMethodDeclaration.class, null);

        while (!p.Line()) {
            SimpleNode node = p.popNode();

            SimpleNode[] render_call = render_decl.search(node, 2);
            if (render_call.length < 1) {
                continue;
            }

            SimpleNode[] nodes = fill_arc_call.search(render_call[0], 3);

            for (int i = 0; i < nodes.length; i++) {
                BSHMethodInvocation circler = (BSHMethodInvocation) nodes[i];

                System.out.println(code.substring(tokenStart(circler.getFirstToken()), tokenEnd(circler.getLastToken())+1));

                BSHArguments args = circler.getArgsNode();
                int[] arg_vals = new int[6];
                boolean args_ok = true;

                for (int j = 0; j < 6; j++) {
                    BSHPrimaryExpression expr = (BSHPrimaryExpression)args.getChild(j);
                    SimpleNode expr_child = expr.getChild(0);

                    if (expr_child instanceof BSHLiteral) {
                        BSHLiteral lit = (BSHLiteral)expr_child;
                        Object val = lit.value;

                        if (Primitive.class.isInstance(val)) {
                            Primitive prim = (Primitive)val;
                            Object prim_val = prim.getValue();

                            if (Integer.class.isInstance(prim_val)) {
                                arg_vals[j] = (Integer)prim_val;
                            } else {
                                args_ok = false;
                            }
                        } else {
                            args_ok = false;
                        }

                    } else {
                        args_ok = false;
                    }
                }

                if (args_ok) {
                    known_circles.add(new Circle(arg_vals[0], arg_vals[1], arg_vals[2], arg_vals[3], arg_vals[4], arg_vals[5]));
                }
            }
        }
    */
    }

}
