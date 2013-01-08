import java.awt.*;
import bsh.Interpreter;
import bsh.EvalError;
import bsh.BshClassManager;

public class AwtTest extends java.applet.Applet implements Runnable {
    Color c;
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
        c = Color.BLACK;
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

    public class ScriptTalker {
        private Graphics g;
        private boolean d;

        public ScriptTalker(Graphics g) {
            this.g = g;
        }

        public void setColor(Color c) {
            g.setColor(c);
        }

        public Color brighter(Color c) {
            return c.brighter();
        }

        public Color darker(Color c) {
            return c.darker();
        }

        public void fillRect(int x, int y, int w, int h) {
            g.fillRect(x,y,w,h);
        }

        public float getBrightness(Color c) {
            int r = c.getRed();
            int g = c.getGreen();
            int b = c.getBlue();
            float [] a = Color.RGBtoHSB(r,g,b, null);

            return a[2];
        }

        public boolean getDir() {
            return d;
        }

        public void setDir(boolean d) {
            this.d = d;
        }
    }

    class ExposeMethodSpec {
        public ExposeMethodSpec(Class c, String n, Class [] a) {
            clas = c;
            name = n;
            args = a;
        };

        private Class<?> clas;
        private String name;
        private Class[] args;

        public void exposeTo(bsh.BshClassManager manager) throws NoSuchMethodException {
            manager.cacheResolvedMethod(clas, args, clas.getMethod(name, args));
        }
    }

    public void paint(Graphics g) {
        ScriptTalker st = new ScriptTalker(g);
        Interpreter bsh = new Interpreter();
        
        ExposeMethodSpec [] exposure = {
            new ExposeMethodSpec( ScriptTalker.class,   "setColor", new Class[]{Color.class} ),
            new ExposeMethodSpec( ScriptTalker.class,   "brighter", new Class[]{Color.class} ),
            new ExposeMethodSpec( ScriptTalker.class,   "darker",   new Class[]{Color.class} ),
            new ExposeMethodSpec( ScriptTalker.class,   "fillRect", new Class[]{int.class, int.class, int.class, int.class} ),
            new ExposeMethodSpec( ScriptTalker.class,   "getBrightness", new Class[]{Color.class} ),
            new ExposeMethodSpec( ScriptTalker.class,   "getDir",   new Class[]{} ),
            new ExposeMethodSpec( ScriptTalker.class,   "setDir",   new Class[]{boolean.class} ),
        };

        try {
            for (ExposeMethodSpec s : exposure) {
                s.exposeTo(bsh.getClassManager());
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            running = false;
            return;
        }

        st.setDir(dir);
        try {
                bsh.set("x", x);
                bsh.set("g", st);
                bsh.set("c", c);
                bsh.eval(
"g.setColor(c);"+
"g.fillRect(0, 0, x++, 100);"+
"if (x > 200) x = 0;"+
"if (g.getDir()) {"+
"   c = g.brighter(c);"+
"   if (g.getBrightness(c) > 0.9) {"+
"       g.setDir(!g.getDir());"+
"   }"+
"} else {"+
"   c = g.darker(c);"+
"   if (g.getBrightness(c) < 0.1) {"+
"       g.setDir(!g.getDir());"+
"   }"+
"}");
                c = (Color)bsh.get("c");
                x = (Integer)bsh.get("x");
        } catch (EvalError e) {
            e.printStackTrace();
            running = false;
            return;
        }

        dir = st.getDir();
    }
}
