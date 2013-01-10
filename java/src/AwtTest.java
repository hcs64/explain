import java.awt.*;
import java.util.HashMap;
import java.io.IOException;
import java.net.MalformedURLException;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;

public class AwtTest extends java.applet.Applet implements Runnable {
    Color c1, c2;
    volatile boolean running = false;
    boolean animating = false;

    Interpreter bsh;
    Thread t;
    Image buffer_image;
    Graphics buffer_graphics;
    Rectangle r = new Rectangle(0,0,0,0);
    String code;

    public void init() {
        running = true;
        t = new Thread(this);
        t.start();

        buffer_image = null;

        bsh = new Interpreter();

        GraphicsWrapper.exposeTo(bsh.getClassManager());

        EPLTalker epl = new EPLTalker("http://"+getCodeBase().getHost()+":9001", "", null, "testpad");
        try {
            epl.connect();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (EPLTalkerException e) {
            e.printStackTrace();
        }

        if (epl.waitForNew()) {
            code = epl.getText();
        }
        else {
            System.err.println("failed getting pad, starting anew");
        }

        if (code == null) {
            code = ""
+"import GraphicsWrapper;"
+"import java.awt.Color;"
+"import java.awt.Font;"
+"int frames = 0;"
+"Font counter_font = new Font(\"Monospaced\", Font.PLAIN, 15);"
+"public void render(GraphicsWrapper g) {"
+"float red = (Math.sin(frames/10.)+1)/2;"
+"g.clearRect(0,0,640,480);"
+"g.setColor(c1);"
+"g.drawLine(frames++, 40, 100, 200);"
+"g.drawOval(150, 180, 10, 10);"
+"g.drawRect(200, 210, 20, 30);"
+"g.setColor(new Color(red,red,red));"
+"g.fillOval(300, 310, 30, 50);"
+"g.fillRect(400, 350, 60, 50);"
+"g.setColor(Color.BLACK);"
+"g.setFont(counter_font);"
+"g.drawString(String.valueOf(frames), frames, 40);"
+"}";
        }

        try {
            bsh.eval(code);
        } catch (EvalError e) {
            e.printStackTrace();
            running = false;
            return;
        }

    }

    public void destroy() {
        running = false;
        try {
            t.join();
        } catch (InterruptedException e) { };
    }

    public void start() {
        c1 = Color.BLACK;
        c2 = Color.RED;

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

        try {
            bsh.set("gw", gw);
            bsh.set("c1", c1);
            bsh.eval("render(gw)");
        } catch (EvalError e) {
            e.printStackTrace();

            running = false;
            return;
        }

        g.drawImage(buffer_image, 0, 0, this);
    }
}
