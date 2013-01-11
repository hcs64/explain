import java.awt.*;
import java.util.HashMap;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;

public class AwtTest extends java.applet.Applet implements Runnable {
    volatile boolean running = false;
    boolean animating = false;

    public interface Renderable {
        public void render(GraphicsWrapper gw);
    };

    Interpreter bsh;
    Renderable bsh_renderable;
    Thread t;
    Image buffer_image;
    Graphics buffer_graphics;
    Rectangle r = new Rectangle(0,0,0,0);

    enum CodeState {
        RUNNING,
        PARSE_ERROR,
        HALTED
    }
    private CodeState code_state;

    String pad_name;
    String code;
    EPLTalker epl;
    EPLTextState server_state;

    public void init() {
        buffer_image = null;

        bsh = new Interpreter();

        pad_name = getParameter("pad_name");
        if (pad_name == null) {
            pad_name = "testpad";
        }

        URL codeBase = getCodeBase();
        try {
            epl = new EPLTalker(new URL(codeBase.getProtocol(), codeBase.getHost(), codeBase.getPort(), ""), "", null, pad_name);
            epl.connect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EPLTalkerException e) {
            e.printStackTrace();
        }

        code = null;
        server_state = null;
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
    }

    public void stop() {
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

        GraphicsWrapper gw = new GraphicsWrapper(buffer_graphics);

        if (epl.hasNew()) {
            String new_code;
            server_state = epl.getServerState();
            new_code = server_state.getText();

            try {
                bsh.eval(new_code);
                bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);

                code = new_code;
                code_state = CodeState.RUNNING;
            } catch (EvalError e) {
                System.out.println("eval error "+e.toString()+", not accepting new code:\n" + code);

                code_state = CodeState.PARSE_ERROR;

                // re-eval old code
                try {
                    bsh.eval(code);
                    bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);

                } catch (EvalError e2) {
                    System.out.println("error " + e2.toString() + " reverting to previously ok code " + code);

                    code_state = CodeState.HALTED;
                }
            }
        }
        
        if (code_state != CodeState.HALTED) {
            try {
                bsh.set("gw", gw);
                bsh_renderable.render(gw);
            } catch (Exception e) {
                System.out.println("HALTING");
                e.printStackTrace();

                code_state = CodeState.HALTED;
            }

            g.drawImage(buffer_image, 0, 0, this);
        }
    }
}
