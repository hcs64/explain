import java.awt.*;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;

public class AwtTest extends java.applet.Applet implements Runnable {
    Color c1, c2;
    boolean dir = true; // true if getting brighter
    volatile boolean running = false;
    boolean animating = false;
    int count = 0;
    int x = 0;
    
    Thread t;

    public void init() {
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
        GraphicsWrapper st = new GraphicsWrapper(g);
        Interpreter bsh = new Interpreter();
        
        GraphicsWrapper.exposeTo(bsh.getClassManager());

        try {
                bsh.set("x", x);
                bsh.set("g", st);
                bsh.set("c1", new ColorWrapper(c1));
                bsh.set("c2", new ColorWrapper(c2));
                bsh.eval( "         g.setColor(c1);    /* set the drawing color*/"
+"         g.drawLine(30, 40, 100, 200);"
+"         g.drawOval(150, 180, 10, 10);"
+"         g.drawRect(200, 210, 20, 30);"
+"         g.setColor(new ColorWrapper(.5f,.5f,.5f));  /* change the drawing color*/"
+"         g.fillOval(300, 310, 30, 50);"
+"         g.fillRect(400, 350, 60, 50);"
);
                x = (Integer)bsh.get("x");
        } catch (EvalError e) {
            e.printStackTrace();
            running = false;
            return;
        }
    }
}
