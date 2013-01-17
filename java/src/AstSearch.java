// a hopefully small engine for doing searches in a Beanshell AST

package bsh;

import java.util.ArrayList;

class AstSearch {
    private String name;
    private Class type;

    // the children we expect to find
    private AstSearch[] subsearches;

    // any can be null if we don't care
    public AstSearch(String name, Class type, AstSearch[] subsearches) {
        // TODO: would be nice to do a regex on the name or token
        this.name = name;
        this.type = type;
        this.subsearches = subsearches;
    }

    // returns all nodes under this tree that match (ignoring any under matched trees)
    // max_depth to -1 to have no max
    public SimpleNode[] search(SimpleNode node, int max_depth) {
        ArrayList<SimpleNode> results = new ArrayList<SimpleNode>();

        searchRecurse(node, results, max_depth);

        SimpleNode[] result_array = new SimpleNode[results.size()];

        return results.toArray(result_array);
    }

    public boolean matches(SimpleNode node) {
        boolean failed = false;

        if (name != null && !name.equals(node.toString())) {
            failed = true;
        }

        if (type != null && !type.isInstance(node)) {
            failed = true;
        }

        int child_count = node.jjtGetNumChildren();
       
        if (subsearches != null) {
            if (child_count != subsearches.length) {
                failed = true;
            } else {
                for (int i = 0; !failed && i < child_count; i++) {
                    if (subsearches[i] != null) {
                        SimpleNode child = node.getChild(i);
                        if (!subsearches[i].matches(child)) {
                            failed = true;
                        }
                    }
                }
            }
        }

        return !failed;
    }


    public void searchRecurse(SimpleNode node, ArrayList<SimpleNode> results, int max_depth) {
        boolean failed = false;

        if (max_depth < 0) {
            max_depth = -1;
        }

        if (matches(node)) {
            // ignore anything deeper and report this match
            results.add(node);

        } else if (max_depth == -1 || max_depth > 0) {
            // recurse on children
            int child_count = node.jjtGetNumChildren();
       
            for (int i = 0; i < child_count; i++) {
                searchRecurse(node.getChild(i), results, max_depth-1);
            }
        }
    }
}
