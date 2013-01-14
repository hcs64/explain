import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.lang.reflect.UndeclaredThrowableException;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;

import epl.Pad;
import epl.PadException;

public class AwtTest extends java.applet.Applet implements Runnable, MouseListener {
    volatile boolean running = false;
    boolean animating = false;

    public interface Renderable {
        public void render(graphics.Graphics gw);
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
    String code;
    Pad pad;

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
        code_state = CodeState.HALTED;

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

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        if (buffer_image == null || getBounds().width != r.width || getBounds().height != r.height) {

            buffer_image = createImage(getBounds().width, getBounds().height);
            buffer_graphics = buffer_image.getGraphics();

            r = getBounds();
        }

        graphics.Graphics gw = new graphics.Graphics2D(buffer_graphics);

        if (pad.hasNew()) {
            String new_code;
            new_code = pad.getClientText();

            try {
                int fiveidx = new_code.indexOf("555");
                if (fiveidx != -1) {
                    pad.makeChange(new_code, fiveidx, 3, "77");
                    new_code = pad.getClientText();
                    //pad.commitChanges();
                }
            } catch (PadException e2) {
                e2.printStackTrace();
            }

            try {
                bsh.eval(new_code);

                code = new_code;
                code_state = CodeState.RUNNING;

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
        
        if (code_state != CodeState.HALTED) {
            try {
                bsh.set("gw", gw);

                bsh_renderable.render(gw);
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

        g.drawImage(buffer_image, 0, 0, this);

    }

    public void mouseClicked(MouseEvent e) {
        try {
            pad.commitChanges();
        } catch (PadException ex) {
            ex.printStackTrace();
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
}
