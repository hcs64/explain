import java.awt.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import bsh.BSHAmbiguousName;
import bsh.BSHArguments;
import bsh.BSHBlock;
import bsh.BSHFormalParameter;
import bsh.BSHFormalParameters;
import bsh.BSHMethodDeclaration;
import bsh.BSHMethodInvocation;
import bsh.BSHPrimaryExpression;
import bsh.BSHPrimitiveType;
import bsh.BSHReturnType;
import bsh.BSHType;

import bsh.Token;
import bsh.SimpleNode;
import bsh.Parser;
import bsh.ParseException;

import epl.Pad;
import epl.Marker;
import epl.PadException;

// the visual programming environment

public class AstUI {
    Font font;

    AstGlobalMethod render_method;

    int render_end_marker_idx;
    ArrayList<AstGlobalMethod> void_methods;
    ArrayList<AstGlobalMethod> primitive_methods;
    ArrayList<AstGlobalMethod> object_methods;
    ArrayList<Element> elements;

    ArrayList<EditRequest> edit_requests;

    RootElement root;

    int[] line_starts;

    public static int[] computeLineStarts(String s) {
        ArrayList<Integer> a = new ArrayList<Integer>();

        int pos = -1;
        do {
            pos++;
            a.add(pos);
        } while ((pos = s.indexOf('\n', pos)) >= 0);

        int[] array = new int[a.size()];
        for (int i = 0; i < a.size(); i++) {
            array[i] = a.get(i);
        }

        return array;
    }

    public int tokenStart(Token t) {
        return line_starts[t.beginLine-1] + t.beginColumn - 1;
    }

    // inclusive, the last char of the token
    public int tokenEnd(Token t) {
        return line_starts[t.endLine-1] + t.endColumn - 1;
    }

    // These are to represent UI/code elements
    abstract class Element extends Region {
        final int start_marker_idx;
        final int end_marker_idx;

        boolean placed;

        public Element(Element old) {
            this.start_marker_idx = old.start_marker_idx;
            this.end_marker_idx = old.end_marker_idx;
            this.name = old.name;
            this.x = old.x;
            this.y = old.y;
            this.width = old.width;
            this.height = old.height;
            this.placed = old.placed;
        }

        public Element(String name, int start_marker_idx, int end_marker_idx) {
            this.start_marker_idx = start_marker_idx;
            this.end_marker_idx = end_marker_idx;
            this.name = name;

            this.placed = false;
        }
    }


    class HolderElement extends Element {
        final AstGlobalMethod method;

        public HolderElement(AstGlobalMethod method, Element old) {
            super(old);
            this.method = method;
        }

        public HolderElement(AstGlobalMethod method, String name, int start_marker_idx, int end_marker_idx) {
            super(name, start_marker_idx, end_marker_idx);
            this.method = method;
        }

        public void placeChildren() {
            int y = this.y;

            int block_elements = method.blockNode.jjtGetNumChildren();

            y+=22;

            for (int i = 0; i < block_elements; i++) {
                SimpleNode node = method.blockNode.getChild(i);
                if ((node instanceof BSHPrimaryExpression) && (node.getChild(0) instanceof BSHMethodInvocation)) {
                    BSHMethodInvocation method_invocation = (BSHMethodInvocation)node.getChild(0);

                    String name = method_invocation.getNameNode().text;
                    BSHArguments args = method_invocation.getArgsNode();
                    int start_line = method_invocation.getFirstToken().beginLine;

                    // now we want to be able to find and add that method node
                    // search backwards in case it is multiply declared
                    for (int j = void_methods.size()-1; j >= 0; j--) {
                        AstGlobalMethod meth = void_methods.get(j);
                        if (meth.decl.name.equals(name)) {
                            // TODO: also match by args?
                            NodeElement ne = new NodeElement(name + " " + start_line, -1, -1);
                            addChild(ne);
                            ne.setX(2);
                            ne.setY(y-this.y);
                            ne.placed = true;

                            y += ne.height + 2;

                            break;
                        }
                    }
                }
            }

            setHeight(y-this.y);
            setWidth(104);
        }

        @Override
        public void update(Graphics g) {
            g.setColor(Color.RED);
            g.fillRect(0, 0, width, height);

            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(name, 10, 15);
        }
    }

    class FactoryElement extends Element {
        final AstGlobalMethod method;

        public FactoryElement(AstGlobalMethod method, Element old) {
            super(old);
            this.method = method;
        }

        public FactoryElement(AstGlobalMethod method, String name, int start_marker_idx, int end_marker_idx) {
            super(name, start_marker_idx, end_marker_idx);
            this.method = method;

            width = 70;
            height = 70;
        }

        @Override
        public void update(Graphics g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, width, height);

            g.setColor(Color.WHITE);
            g.setFont(font);
            g.drawString(method.decl.name, 5, 20);
        }

        @Override
        public boolean mousePress(int x, int y) {
            addToRender(method);
            return true;
        }

    }

    class NodeElement extends Element {
        public NodeElement(String name, int start_marker_idx, int end_marker_idx) {
            super(name, start_marker_idx, end_marker_idx);

            width = 100;
            height = 100;
        }

        @Override
        public void update(Graphics g) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 100, 100);

            g.setColor(Color.WHITE);
            g.fillRect(2, 2, 100-4, 20-4);

            g.setColor(Color.BLACK);
            g.setFont(font);
            g.drawString(name, 10, 15);
        }
    }

    public void init() {
        font = new Font("SansSerif", Font.BOLD, 12);
        root = new RootElement("root");
        render_method = null;
        render_end_marker_idx = -1;

        line_starts = null;
        edit_requests = new ArrayList<EditRequest> ();

        void_methods = new ArrayList<AstGlobalMethod> ();
        primitive_methods = new ArrayList<AstGlobalMethod> ();
        object_methods = new ArrayList<AstGlobalMethod> ();
        elements = new ArrayList<Element> ();
    }

    public void generateUI(String code, Pad pad, Marker[] markers) throws ParseException, PadException {
        ArrayList<Element> new_elements = new ArrayList<Element>();

        root.clearChildren();

        render_method = null;

        int factory_x = 10;
        int factory_y = 300;

        void_methods.clear();
        primitive_methods.clear();
        object_methods.clear();

        line_starts = computeLineStarts(code);

        // parse to find all method declarations
        StringReader sr = new StringReader(code);
        Parser p = new Parser(sr);

        ArrayList<SimpleNode> decls = new ArrayList<SimpleNode>();
        AstSearch decl_search = new AstSearch(null, BSHMethodDeclaration.class, null);

        while (!p.Line()) {
            SimpleNode node = p.popNode();

            decl_search.searchRecurse(node, decls, 0);
        }

        for (int i = 0; i < decls.size(); i++) {
            BSHMethodDeclaration decl = (BSHMethodDeclaration)decls.get(i);
            BSHReturnType returnTypeNode = null;
            BSHFormalParameters paramsNode = null;
            BSHBlock blockNode = null;

            for (int j = 0; j < decl.jjtGetNumChildren(); j++) {

                SimpleNode node = decl.getChild(j);
                if (node instanceof BSHReturnType) {
                    returnTypeNode = (BSHReturnType)node;
                } else if (node instanceof BSHFormalParameters) {
                    paramsNode = (BSHFormalParameters)node;
                } else if (node instanceof BSHBlock) {
                    blockNode = (BSHBlock)node;
                }
            }


            if (returnTypeNode != null && paramsNode != null && blockNode != null) {
                AstGlobalMethod method = new AstGlobalMethod(decl, returnTypeNode, paramsNode, blockNode);

                // attempt to match with a method from the old code
                int start_pos = tokenStart(decl.getFirstToken());
                int end_pos = tokenEnd(decl.getLastToken());
                int start_pos_idx;
                int end_pos_idx;

                Element old_elem = findElementByTextPos(markers, start_pos, end_pos);
                if (old_elem != null) {
                    // matched, copy state from the old one
                    System.out.println("matched "+old_elem.name+" with "+decl.name);

                    start_pos_idx = old_elem.start_marker_idx;
                    end_pos_idx = old_elem.end_marker_idx;
                } else {
                    start_pos_idx = pad.registerMarker(start_pos, true, true); // a marker before the first char
                    end_pos_idx = pad.registerMarker(end_pos, false, true); // a marker after the last char

                }


                if (method.returnTypeNode.isVoid) {
                    if (decl.name.equals("render")) {
                        // may want to check params as well

                        int render_body_end_pos = tokenEnd(decl.getLastToken());
                        render_method = method;

                        if (render_end_marker_idx == -1) {
                            render_end_marker_idx = pad.registerMarker(render_body_end_pos, true, true);
                        } else {
                            pad.reRegisterMarker(render_end_marker_idx, render_body_end_pos, true, true);
                        }

                        {
                            HolderElement he;
                            
                            if (old_elem == null) {
                                he = new HolderElement(method, decl.name, start_pos_idx, end_pos_idx);
                            } else {
                                he = new HolderElement(method, old_elem);
                            }

                            he.x = 400-104;
                            he.y = 300;
                            he.placeChildren();
                            he.placed = true;

                            new_elements.add(he);
                            root.addChild(he);
                        }
                    } else {
                        void_methods.add(method);

                        {
                            FactoryElement fe;

                            if (old_elem == null) {
                                fe = new FactoryElement(method, decl.name, start_pos_idx, end_pos_idx);
                            } else {
                                fe = new FactoryElement(method, old_elem);
                            }

                            fe.x = factory_x;
                            fe.y = factory_y;
                            fe.placed = true;

                            factory_x += 104;

                            new_elements.add(fe);
                            root.addChild(fe);
                        }
                    }
                } else {
                    if (method.primitiveReturnType != null) {
                        primitive_methods.add(method);
                    } else if (method.ambiguousNameReturnType != null) {
                        object_methods.add(method);
                    }
                }
            }
        }

        elements = new_elements;
    }

    Element findElementByTextPos(Marker[] markers, int start_pos, int end_pos) {
        for (Element e : elements) {
            if (e.start_marker_idx >= 0 && e.end_marker_idx >= 0) {
                Marker start_marker = markers[e.start_marker_idx];
                Marker end_marker = markers[e.end_marker_idx];

                if (start_marker.valid && end_marker.valid && start_marker.pos == start_pos && end_marker.pos == end_pos) {
                    return e;
                }
            }
        }

        return null;
    }

    class RootElement extends Region {
        public RootElement(String name) {
            this.name = name;
        }
    }

    void render(Graphics g, int width, int height) {
        root.setWidth(width);
        root.setHeight(height);

        root.redraw(g);
    }

    public abstract class EditRequest {
        public abstract void apply(Pad pad) throws PadException;
    }

    public class InsertRequest extends EditRequest {
        final int marker_idx;
        String news;
        final boolean follow;

        public InsertRequest(int marker_idx, String news, boolean follow) {
            this.marker_idx = marker_idx;
            this.news = news;
            this.follow = follow;
        }

        public void apply(Pad pad) throws PadException {
            pad.insertAtMarker(marker_idx, news, follow);
        }
    }

    static public class MouseEvent {
        final public int type, x, y;

        public MouseEvent(int type, int x, int y) {
            this.type = type;
            this.x = x;
            this.y = y;
        }
    }

    public ArrayList<EditRequest> gatherUIRequests(ConcurrentLinkedQueue<MouseEvent> events) {
        edit_requests.clear();

        while (!events.isEmpty()) {
            MouseEvent e = events.poll();
            root.mouseEvent(e.type, e.x, e.y);
        }

        return edit_requests;
    }

    public void addToRender(AstGlobalMethod m) {
        if (render_method != null) {
            int endPos = tokenEnd(render_method.blockNode.getLastToken());

            StringBuilder invocation = new StringBuilder();

            invocation.append('\n');
            invocation.append(m.decl.name);
            invocation.append('(');

            int params = m.paramsNode.jjtGetNumChildren();
            for (int i = 0; i < params; i++) {
                BSHFormalParameter p = (BSHFormalParameter)m.paramsNode.getChild(i);
                SimpleNode type_node = ((BSHType)p.getChild(0)).getTypeNode();
                String param_name = p.name;

                if (type_node instanceof BSHAmbiguousName) {
                    String param_type = ((BSHAmbiguousName)type_node).text;
                    String default_val = "null";

                    if (param_type.equals("Color") || param_type.endsWith(".Color")) {
                        default_val = "Color.RED";
                    } else if (param_type.equals("Graphics2D")) {
                        default_val = "g";
                    }

                    invocation.append(default_val);
                } else if (type_node instanceof BSHPrimitiveType) {
                    Class real_type = ((BSHPrimitiveType)type_node).getType();

                    if (real_type == int.class) {
                        int default_val = 10;

                        // we'll probably want to stir these around a bit
                        if (param_name.equals("x")) {
                            default_val = 100;
                        } else if (param_name.equals("y")) {
                            default_val = 100;
                        } else if (param_name.equals("radius")) {
                            default_val = 50;
                        } else if (param_name.equals("side")) {
                            default_val = 100;
                        } else if (param_name.equals("size")) {
                            default_val = 100;
                        }

                        invocation.append(default_val);
                    }
                }

                if (i < params-1) {
                    invocation.append(", ");
                }
            }

            invocation.append(");\n");

            edit_requests.add(new InsertRequest(render_end_marker_idx, invocation.toString(), true));

        }
    }

}
