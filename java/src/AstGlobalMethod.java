
import bsh.BSHAmbiguousName;
import bsh.BSHBlock;
import bsh.BSHFormalParameters;
import bsh.BSHMethodDeclaration;
import bsh.BSHPrimitiveType;
import bsh.BSHReturnType;
import bsh.BSHType;
import bsh.SimpleNode;

class AstGlobalMethod {
    final public BSHMethodDeclaration decl;
    final public BSHFormalParameters paramsNode;
    final public BSHBlock blockNode;

    final public BSHReturnType returnTypeNode;

    final public String ambiguousNameReturnType;
    final public Class primitiveReturnType;

    public AstGlobalMethod(BSHMethodDeclaration decl, BSHReturnType returnTypeNode, BSHFormalParameters paramsNode, BSHBlock blockNode) {
        this.decl = decl;
        this.returnTypeNode = returnTypeNode;
        this.paramsNode = paramsNode;
        this.blockNode = blockNode;

        if (returnTypeNode.isVoid) {
            this.ambiguousNameReturnType = null;
            this.primitiveReturnType = null;
        } else {
            BSHType return_type = returnTypeNode.getTypeNode();
            SimpleNode type = return_type.getTypeNode();

            if (type instanceof BSHPrimitiveType) {
                BSHPrimitiveType prim_type = (BSHPrimitiveType)type;
                Class real_type = prim_type.getType();

                this.primitiveReturnType = real_type;
                this.ambiguousNameReturnType = null;
            } else if (type instanceof BSHAmbiguousName) {
                BSHAmbiguousName name = (BSHAmbiguousName)type;

                this.ambiguousNameReturnType = name.text;
                this.primitiveReturnType = null;
            } else {
                this.ambiguousNameReturnType = null;
                this.primitiveReturnType = null;
            }
        }
    }

}
