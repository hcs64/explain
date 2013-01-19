
import java.util.*;
import java.awt.*;

public class Region
{
   public static final int DRAG    = 0;
   public static final int ENTER   = 1;
   public static final int EXIT    = 2;
   public static final int MOVE    = 3;
   public static final int PRESS   = 4;
   public static final int RELEASE = 5;

   public void update(Graphics g) {
   }

   public void overlay(Graphics g) {
   }

   public boolean isDebug() {
      return false;
   }

   public void clearChildren() {
      nChildren = 0;
      children.clear();
   }

   public Region addChild() {
      return addChild(new Region());
   }

   public Region addChild(Region child) {
      if (nChildren == children.size())
         children.add(child);
      else
         children.set(nChildren, child);
      child.parent = this;
      nChildren++;
      return child;
   }

   public void setChild(int n, Region child) {
      children.set(n, child);
      child.parent = this;
   }

   public void removeChild(int n) {
      child(n).parent = null;
      children.remove(n);
      --nChildren;
   }

   public void removeChild(Region child) {
      children.remove(child);
      child.parent = null;
      --nChildren;
   }

   public Region findRegion(int x, int y) {
      x -= this.x;
      y -= this.y;
      for (int n = nChildren - 1 ; n >= 0 ; n--)
         if (child(n).contains(x, y))
	    return child(n).findRegion(x, y);
      return this;
   }

   public boolean contains(int x, int y) {
      return x >= this.x && x < this.x + width &&
             y >= this.y && y < this.y + height ;
   }

   public boolean isOver() { return isOver; }

   public Region parent() {
      return parent;
   }

   public Region child(int n) {
      return (Region)children.get(n);
   }

   public Region getDescendantWithFocus() {
      return descendantWithFocus;
   }

   public int getGlobalX() {
      return gx;
   }

   public int getGlobalY() {
      return gy;
   }

   public String getName() {
      return name;
   }

   public int getHeight() {
      return height;
   }

   public int getWidth() {
      return width;
   }

   public int getX() {
      return x;
   }

   public int getY() {
      return y;
   }

   public boolean isDamage() {
      return isDamage;
   }

   public boolean keyPress(int key) { return false; }
   public boolean keyRelease(int key) { return false; }

   public boolean keyEvent(int eventType, int key) {
      Region region = descendantWithFocus;
      switch (eventType) {

      case PRESS:
         for (Region r = region ; r != null ; r = r.parent)
	    if (r.keyPress(key))
	       return r.keyPress(key);
         return keyPress(key);

      case RELEASE:
         for (Region r = region ; r != null ; r = r.parent)
	    if (r.keyRelease(key))
	       return r.keyRelease(key);
         return keyRelease(key);
      }

      return false;
   }

   public void mouseDrag(int x, int y) { }
   public void mouseEnter(int x, int y) { }
   public void mouseExit(int x, int y) { }
   public boolean mouseMove(int x, int y) { return false; }
   public boolean mousePress(int x, int y) { return false; }
   public void mouseRelease(int x, int y) { }

   public boolean mouseEvent(int eventType, int x, int y) {
      x -= this.x;
      y -= this.y;
      switch (eventType) {

      case MOVE:

         boolean didContainCursor = containsCursor;

         containsCursor = contains(x + this.x, y + this.y);

	 if (! didContainCursor && containsCursor)
	    mouseEnter(x, y);

	 if (didContainCursor && ! containsCursor)
	    mouseExit(x, y);

	 boolean hasChildWithMove = false;
         for (int n = nChildren - 1 ; n >= 0 ; n--)
	    hasChildWithMove |= child(n).mouseEvent(MOVE, x, y);

         isOver = containsCursor && ! hasChildWithMove;

	 if (isOver)
	    mouseMove(x, y);

	 return containsCursor;

      case PRESS:

	 hasFocus = false;
	 descendantWithFocus = this;

         boolean containsFocus = false;
         for (int n = nChildren - 1 ; n >= 0 ; n--)
	    containsFocus |= child(n).mouseEvent(PRESS, x, y) &&
                             child(n).contains(x, y);

         if ( ! containsFocus &&
	      contains(x + this.x, y + this.y) &&
	      (hasFocus = mousePress(x, y)) )
	    for (Region ancestor = parent ; ancestor != null ; ancestor = ancestor.parent)
	        ancestor.descendantWithFocus = this;

	 return hasFocus || containsFocus;

      case DRAG:
      case RELEASE:

         if (! hasFocus)
            for (int n = nChildren - 1 ; n >= 0 ; n--)
	       child(n).mouseEvent(eventType, x, y);
	 else if (eventType == DRAG)
	    mouseDrag(x, y);
         else
	    mouseRelease(x, y);
         return false;
      }

      return false;
   }

   public int nChildren() {
      return nChildren;
   }

   public void setName(String name) {
      this.name = name;
   }

   public void setDamage(boolean isDamage) {
      this.isDamage = isDamage;
   }

   public void setHeight(int height) {
      this.height = height;
   }

   public void setLocation(int x, int y) {
      this.x = x;
      this.y = y;
   }

   public void setSize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public void setWidth(int width) {
      this.width = width;
   }

   public void setX(int x) {
      this.x = x;
   }

   public void setY(int y) {
      this.y = y;
   }

   public String toString() {
      return "{\"" + name + "\" " + x + "," + y + "," + width + "," + height + "}";
   }

   public void redraw(Graphics g) {
      gx = x + (parent == null ? 0 : parent.gx);
      gy = y + (parent == null ? 0 : parent.gy);

      update(g);
      for (int n = 0 ; n < nChildren ; n++) {
         Region c = child(n);
         if (c.G != null) {
            Rectangle r = c.G.getClipBounds();
	    if (r.x != c.x || r.y != c.y || r.width != c.width || r.height != c.height)
	       c.G = null;
         }
         if (c.G == null)
	    c.G = g.create(c.x, c.y, c.width, c.height);
	 c.redraw(c.G);
      }
      overlay(g);
   }

   protected String name = "";
   protected int x, y, width, height, nChildren, gx, gy;
   protected boolean isDamage, hasFocus, isOver, containsCursor;
   ArrayList children = new ArrayList();
   Region parent, descendantWithFocus = this;
   Graphics G;
}

