import java.awt.*;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;
import bsh.Capabilities;

public class AwtTest extends java.applet.Applet implements Runnable {
    Color c1, c2;
    boolean dir = true; // true if getting brighter
    volatile boolean running = false;
    boolean animating = false;
    int count = 0;

    Interpreter bsh;
    Thread t;

    public void init() {
        running = true;
        t = new Thread(this);
        t.start();

        bsh = new Interpreter();

        GraphicsWrapper.exposeTo(bsh.getClassManager());

        try {
            bsh.eval(""
+"import GraphicsWrapper;"
+"import java.lang.Math.*;"
+"int frames = 0;"
+"public void render(GraphicsWrapper g) {"
+"float red = (Math.sin(frames/10.)+1)/2;"
+"g.setColor(c1);"
+"g.drawLine(frames++, 40, 100, 200);"
+"g.drawOval(150, 180, 10, 10);"
+"g.drawRect(200, 210, 20, 30);"
+"g.setColor(new Color(red,.5f,.5f));"
+"g.fillOval(300, 310, 30, 50);"
+"g.fillRect(400, 350, 60, 50);"
+"}"
);
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
        count ++;
        if (count > 2) {
            paint(g);
            count = 0;
        }
    }

    public void paint(Graphics g) {
        GraphicsWrapper gw = new GraphicsWrapper(g);

        try {
            bsh.set("gw", gw);
            bsh.set("c1", c1);
            bsh.eval("render(gw)");
        } catch (EvalError e) {
            e.printStackTrace();

            running = false;
            return;
        }
    }
}
