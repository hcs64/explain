package bsh;

import java.awt.*;
import java.util.ArrayList;

class AstGlobalMethod {
    final public BSHMethodDeclaration method;
    final public BSHFormalParameters paramsNode;
    final public BSHBlock blockNode;

    final public BSHReturnType returnTypeNode;
    final public String ambiguousNameReturnType;
    final public Class primitiveReturnType;

    public AstGlobalMethod(BSHMethodDeclaration method, BSHReturnType returnTypeNode, BSHFormalParameters paramsNode, BSHBlock blockNode) {
        this.method = method;
        this.returnTypeNode = returnTypeNode;
        this.paramsNode = paramsNode;
        this.blockNode = blockNode;
        this.ambiguousNameReturnType = null;
        this.primitiveReturnType = null;
    }

    public AstGlobalMethod(BSHMethodDeclaration method, BSHReturnType returnTypeNode, BSHFormalParameters paramsNode, BSHBlock blockNode, String ambiguousNameReturnType) {
        this.method = method;
        this.returnTypeNode = returnTypeNode;
        this.paramsNode = paramsNode;
        this.blockNode = blockNode;
        this.ambiguousNameReturnType = ambiguousNameReturnType;
        this.primitiveReturnType = null;
    }

    public AstGlobalMethod(BSHMethodDeclaration method, BSHReturnType returnTypeNode, BSHFormalParameters paramsNode, BSHBlock blockNode, Class primitiveReturnType) {
        this.method = method;
        this.returnTypeNode = returnTypeNode;
        this.paramsNode = paramsNode;
        this.blockNode = blockNode;
        this.ambiguousNameReturnType = null;
        this.primitiveReturnType = primitiveReturnType;
    }

    public void renderFactory(Graphics2D g, int x, int y) {
        g.setColor(Color.BLACK);
        g.fillRect(x, y, 70, 50);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(method.name, x+5, y+20);
    }

    public void renderNode(Graphics2D g, long t, int x, int y, String title) {
        g.setColor(Color.BLACK);
        g.fillRect(x, y, 100, 100);

        g.setColor(Color.WHITE);
        g.fillRect(x+2, y+2, 100-4, 20-4);

        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString(title, x+10, y+15);
    }

    public void renderAsHolder(Graphics2D g, long t, int x, int y, ArrayList<AstGlobalMethod> void_methods, ArrayList<AstGlobalMethod> primitive_methods, ArrayList<AstGlobalMethod> object_methods) {
        int block_elements = blockNode.jjtGetNumChildren();

        g.setColor(Color.RED);
        g.fillRect(x, y, 104, 20);

        x+=2;
        y+=2;

        for (int i = 0; i < block_elements; i++) {
            SimpleNode node = blockNode.getChild(i);
            if ((node instanceof BSHPrimaryExpression) && (node.getChild(0) instanceof BSHMethodInvocation)) {
                BSHMethodInvocation method_invocation = (BSHMethodInvocation)node.getChild(0);

                String name = method_invocation.getNameNode().text;
                BSHArguments args = method_invocation.getArgsNode();

                // now we want to be able to find and render that method
                // search backwards in case it is multiply declared...
                for (int j = void_methods.size()-1; j >= 0; j--) {
                    AstGlobalMethod meth = void_methods.get(j);
                    if (meth.method.name.equals(name)) {
                        g.setColor(Color.RED);
                        g.fillRect(x-2, y, 104, 102);

                        meth.renderNode(g, t, x, y, name + " " + method_invocation.firstToken.beginLine);
                        y += 102;

                        break;
                    }
                }
            }
        }
    }
}
