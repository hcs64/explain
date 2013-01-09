import java.awt.*;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;
import bsh.Capabilities;

public class AwtTest extends java.applet.Applet implements Runnable {
    Color c1, c2;
    volatile boolean running = false;
    boolean animating = false;

    Interpreter bsh;
    Thread t;
    Image buffer_image;
    Graphics buffer_graphics;


    public void init() {
        running = true;
        t = new Thread(this);
        t.start();

        buffer_image = createImage(bounds().width, bounds().height);
        buffer_graphics = buffer_image.getGraphics();

        bsh = new Interpreter();

        GraphicsWrapper.exposeTo(bsh.getClassManager());

        try {
            bsh.eval(""
+"import GraphicsWrapper;"
+"import java.awt.Color;"
+"int frames = 0;"
+"public void render(GraphicsWrapper g) {"
+"float red = (Math.sin(frames/10.)+1)/2;"
+"g.clearRect(0,0,640,480);"
+"g.setColor(c1);"
+"g.drawLine(frames++, 40, 100, 200);"
+"g.drawOval(150, 180, 10, 10);"
+"g.drawRect(200, 210, 20, 30);"
+"g.setColor(new Color(red,.5f,.5f));"
+"g.fillOval(300, 310, 30, 50);"
+"g.fillRect(400, 350, 60, 50);"
+"g.setColor(Color.BLACK);"
+"g.drawString(String.valueOf(frames), frames, 40);"
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
        paint(g);
    }

    public void paint(Graphics g) {
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
