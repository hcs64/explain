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
    String fallback_code;
    String code;
    EPLTalker epl;

    public void init() {
        buffer_image = null;

        bsh = new Interpreter();

        GraphicsWrapper.exposeTo(bsh.getClassManager());

        URL codeBase = getCodeBase();
        try {
            epl = new EPLTalker(new URL(codeBase.getProtocol(), codeBase.getHost(), codeBase.getPort(), ""), "", null, "testpad");
            epl.connect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EPLTalkerException e) {
            e.printStackTrace();
        }

        fallback_code = ""
+"import GraphicsWrapper;\n"
+"import java.awt.Color;\n"
+"import java.awt.Font;\n"
+"int frames = 0;\n"
+"Font counter_font = new Font(\"Monospaced\", Font.PLAIN, 15);\n"
+"public void render(GraphicsWrapper g) {\n"
+"float red = (Math.sin(frames/10.)+1)/2;\n"
+"g.clearRect(0,0,640,480);\n"
+"g.setColor(Color.BLUE);\n"
+"g.drawLine(frames++, 40, 100, 200);\n"
+"g.drawOval(150, 180, 10, 10);\n"
+"g.drawRect(200, 210, 20, 30);\n"
+"g.setColor(new Color(red,red,red));\n"
+"g.fillOval(300, 310, 30, 50);\n"
+"g.fillRect(400, 350, 60, 50);\n"
+"g.setColor(Color.BLACK);\n"
+"g.setFont(counter_font);\n"
+"g.drawString(String.valueOf(frames), frames, 40);\n"
+"}\n";

        code = "public void render(GraphicsWrapper) {}";

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
            tryRenderNewCode(epl.getText(), gw);
        } else if (bsh_renderable != null) {
            try {
                bsh.set("gw", gw);
                bsh_renderable.render(gw);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        g.drawImage(buffer_image, 0, 0, this);
    }

    private void tryEvalNewCode(String newcode) {
        try {
            bsh.eval(newcode);
            bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);
            code = newcode;

        } catch (EvalError e) {
            System.out.println("eval-time exception "+e.toString()+", not accepting:\n" + code);
            e.printStackTrace();

            try {
                code = fallback_code;
                bsh.eval(code);
                bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);
            } catch (EvalError e2) {
                System.err.println("unexpected error eval'ing fallback");
                e2.printStackTrace();

                bsh_renderable = null;
            }
        }
    }

    private void tryRenderNewCode(String newcode, GraphicsWrapper gw) {
        tryEvalNewCode(newcode);

        try {
            bsh.set("gw", gw);
            bsh_renderable.render(gw);

            // rendered ok, save as fallback
            fallback_code = code;
        } catch (Exception e) {
            // any runtime exception prevents us from accepting the new code
            //e.printStackTrace();

            System.out.println("runtime exception "+e.toString()+", not accepting:\n" + code);
            code = fallback_code;

            try {
                bsh.eval(code);
                bsh_renderable = (Renderable) bsh.getInterface(Renderable.class);
                bsh.set("gw", gw);
                bsh_renderable.render(gw);
            } catch (EvalError e2) {
                System.err.println("unexpected error eval'ing fallback");
                e2.printStackTrace();

                bsh_renderable = null;
            }
        }
    }
}
