import java.awt.*;

public class ColorWrapper {
    private Color c;

    public ColorWrapper(Color c) {
        this.c = c;
    }

    static final Class[] args_1int = new Class[]{int.class};
    static final Class[] args_3flt = new Class[]{float.class, float.class, float.class};
    static final Class[] args_4flt = new Class[]{float.class, float.class, float.class, float.class};
    static final Class[] args_3int = new Class[]{int.class, int.class, int.class};
    static final Class[] args_4int = new Class[]{int.class, int.class, int.class, int.class};
    static final Class[] args_flta = new Class[]{float[].class};
    static final Class[] args_void = new Class[]{};

    static final Class[][] ctorArgs = {
        args_3flt, args_4flt, args_1int, new Class[]{int.class, boolean.class},
        args_3int, args_4int, 
    };

    static final String[] methodNames = {
        "brighter", "darker",   "decode",   "equals",
        "getAlpha", "getBlue",  "getColor", "getColor",
        "getColor", "getColorComponents", "getGreen", "getHSBColor",
        "getRed",   "getRGB",   "getRGBColorComponents",    "getRGBComponents",
        "getTransparency", "hashCode", "HSBtoRGB",  "RGBtoHSB",
        "toString"
    };

    static final Class[][] methodArgs= {
        args_void, args_void, new Class[]{String.class}, new Class[]{Object.class},
        args_void, args_void, new Class[]{String.class}, new Class[]{String.class, ColorWrapper.class},
        new Class[]{String.class, int.class}, args_flta, args_void, args_3flt,
        args_void, args_void, args_flta, args_flta,
        args_void, args_void, args_3flt, new Class[]{int.class, int.class, int.class, float[].class},
        args_void
    };

    static final ExposeMethodSpec exposures[] = ExposeMethodSpec.buildArray(ColorWrapper.class, methodNames, methodArgs, methodNames.length, ctorArgs, ctorArgs.length);

    public static void exposeTo(bsh.BshClassManager manager) {
        for (ExposeMethodSpec s: exposures) {
            try {
                s.exposeTo(manager);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    // standard Color constructors

    //public ColorWrapper(ColorSpace cspace, float[] components, float alpha) {
    //}

    public ColorWrapper(float r, float g, float b) {
        this.c = new Color(r, g, b);
    }

    public ColorWrapper(float r, float g, float b, float a) {
        this.c = new Color(r, g, b, a);
    }

    public ColorWrapper(int rgb) {
        this.c = new Color(rgb);
    }

    public ColorWrapper(int rgba, boolean hasalpha) {
        this.c = new Color(rgba, hasalpha);
    }

    public ColorWrapper(int r, int g, int b) {
        this.c = new Color(r, g, b);
    }

    public ColorWrapper(int r, int g, int b, int a) {
        this.c = new Color(r, g, b, a);
    }


    //

    public Color unwrap() {
        return c;
    }

    // standard Color methods

    public ColorWrapper brighter() {
        return new ColorWrapper(c.brighter());
    }

    //public createContext(ColorModel cm, Rectangle r, Rectangle2D r2d, AffineTransform xform, RenderingHints hints) {
    //}

    public ColorWrapper darker() {
        return new ColorWrapper(c.darker());
    }

    public static ColorWrapper decode(String nm) {
        return new ColorWrapper(Color.decode(nm));
    }

    public boolean equals(Object obj) {
        return c.equals(obj);
    }

    public int getAlpha() {
        return c.getAlpha();
    }

    public int getBlue() {
        return c.getBlue();
    }

    public static ColorWrapper getColor(String nm) {
        return new ColorWrapper(Color.getColor(nm));
    }

    public static ColorWrapper getColor(String nm, ColorWrapper v) {
        return new ColorWrapper(Color.getColor(nm, v.unwrap()));
    }

    public static ColorWrapper getColor(String nm, int v) {
        return new ColorWrapper(Color.getColor(nm, v));
    }

    //public float[] getColorComponents(ColorSpace cspace, float[] compArray) {
    //}

    public float[] getColorComponents(float [] compArray) {
        return c.getColorComponents(compArray);
    }

    //public ColorSpace getColorSpace() {
    //}

    public int getGreen() {
        return c.getGreen();
    }

    public static ColorWrapper getHSBColor(float h, float s, float b) {
        return new ColorWrapper(Color.getHSBColor(h, s, b));
    }

    public int getRed() {
        return c.getRed();
    }

    public int getRGB() {
        return c.getRGB();
    }

    public float[] getRGBColorComponents(float[] compArray) {
        return c.getRGBColorComponents(compArray);
    }

    public float[] getRGBComponents(float[] compArray) {
        return c.getRGBComponents(compArray);
    }

    public int getTransparency() {
        return c.getTransparency();
    }

    public int hashCode() {
        return c.hashCode();
    }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        return Color.HSBtoRGB(hue, saturation, brightness);
    }

    public static float[] RGBtoHSB(int r, int g, int b, float[] hsbvals) {
        return Color.RGBtoHSB(r, g, b, hsbvals);
    }

    public String toString() {
        return c.toString();
    }
}
