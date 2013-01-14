package graphics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Map;

public class Graphics2D extends Graphics {
    private java.awt.Graphics2D g2d;

    @Override
    public java.awt.Graphics2D unwrap() {
        return g2d;
    }

    public Graphics2D(java.awt.Graphics g) {
        super(g);
        g2d = (java.awt.Graphics2D) g;
    }

    public void addRenderingHints(Map hints) {
        g2d.addRenderingHints(hints);
    }

    public void clip(Shape s) {
        g2d.clip(s);
    }

    public void draw(Shape s) {
        g2d.draw(s);
    }

    public void draw3DRect(int x, int y, int width, int height, boolean raised) {
        g2d.draw3DRect(x, y, width, height, raised);
    }

    public void drawGlyphVector(GlyphVector g, float x, float y) {
        g2d.drawGlyphVector(g, x, y);
    }

    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        g2d.drawImage(img, op, x, y);
    }

    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        return g2d.drawImage(img, xform, obs);
    }

    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        g2d.drawRenderableImage(img, xform);
    }

    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        g2d.drawRenderedImage(img, xform);
    }

    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        g2d.drawString(iterator, x, y);
    }

    @Override
    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        g2d.drawString(iterator, x, y);
    }

    public void drawString(String str, float x, float y) {
        g2d.drawString(str, x, y);
    }

    @Override
    public void drawString(String str, int x, int y) {
        g2d.drawString(str, x, y);
    }

    public void fill(Shape s) {
        g2d.fill(s);
    }

    @Override
    public void fill3DRect(int x, int y, int width, int height, boolean raised) {
        g2d.fill3DRect(x, y, width, height, raised);
    }

    public Color getBackground() {
        return g2d.getBackground();
    }

    public Composite getComposite() {
        return g2d.getComposite();
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        return g2d.getDeviceConfiguration();
    }

    public FontRenderContext getFontRenderContext() {
        return g2d.getFontRenderContext();
    }

    public Paint getPaint() {
        return g2d.getPaint();
    }

    public Object getRenderingHint(RenderingHints.Key hintKey) {
        return g2d.getRenderingHint(hintKey);
    }

    public RenderingHints getRenderingHints() {
        return g2d.getRenderingHints();
    }

    public Stroke getStroke() {
        return g2d.getStroke();
    }

    public AffineTransform getTransform() {
        return g2d.getTransform();
    }

    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        return g2d.hit(rect, s, onStroke);
    }

    public void rotate(double theta) {
        g2d.rotate(theta);
    }

    public void rotate(double theta, double x, double y) {
        g2d.rotate(theta, x, y);
    }

    public void scale(double sx, double sy) {
        g2d.scale(sx, sy);
    }

    public void setBackground(Color color) {
        g2d.setBackground(color);
    }

    public void setComposite(Composite comp) {
        g2d.setComposite(comp);
    }

    public void setPaint(Paint paint) {
        g2d.setPaint(paint);
    }

    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        g2d.setRenderingHint(hintKey, hintValue);
    }

    public void setStroke(Stroke s) {
        g2d.setStroke(s);
    }

    public void setTransform(AffineTransform Tx) {
        g2d.setTransform(Tx);
    }

    public void shear(double shx, double shy) {
        g2d.shear(shx, shy);
    }

    public void transform(AffineTransform Tx) {
        g2d.transform(Tx);
    }

    public void translate(double tx, double ty) {
        g2d.translate(tx, ty);
    }

    public void translate(int x, int y) {
        g2d.translate(x, y);
    }

}
