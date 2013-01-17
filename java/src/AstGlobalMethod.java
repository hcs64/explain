package bsh;

import java.awt.*;

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

    public void renderNode(Graphics2D g, long t, int x, int y) {
        g.setColor(Color.BLACK);
        g.fillRect(x, y, 100, 100);

        g.setColor(Color.WHITE);
        g.fillRect(x+2, y+2, 100-4, 20-4);
    }
}
