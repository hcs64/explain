import java.util.ArrayList;

public class EPLChangesetTests {
    static int test_count = 0;
    static int test_fails = 0;

    public static void doTest(String start, EPLChangeset ep, String end) throws EPLChangesetException {
        test_count ++;
        String result = ep.applyToText(start);
        System.out.println(result);
        if (!end.equals(result)) {
            System.out.println("'" + result + "' != '" + end + "'");
            test_fails ++;
            System.out.println(ep.explain());
        }
    }

    public static EPLChangeset doSimpleTest(String start, String str, int pos, int replacing, String end) throws EPLChangesetException {
        EPLChangeset ep = new EPLChangeset(EPLChangeset.makeSimpleEdit(str, pos, replacing, start.length()));
        doTest(start, ep, end);
        return ep;
    }

    public static void doCompositeTest(String start, EPLChangeset old, String str, int pos, int replacing) throws EPLChangesetException {
        EPLChangeset ep_simple = new EPLChangeset(EPLChangeset.makeSimpleEdit(str, pos, replacing, old.newLen));
        EPLChangeset ep = new EPLChangeset(EPLChangeset.composeSimpleEdit(old.toString(), str, pos, replacing, old.newLen));
        System.out.println(old.toString() + " + " + ep_simple.toString());
        System.out.println(ep.toString());
        String end = ep_simple.applyToText(old.applyToText(start));
        doTest(start, ep, end);
    }

    public static void main(String args[]) throws EPLChangesetException {
        String ten = "0123456789";

        EPLChangeset[] a1 = {
            doSimpleTest(ten, "hello", 0, 0, "hello" + ten),
            doSimpleTest(ten, "hello", 1, 0, "0hello123456789"),
            doSimpleTest(ten, "hello", 2, 0, "01hello23456789"),
            doSimpleTest(ten, "hello", 10, 0, ten + "hello"),
            doSimpleTest(ten, "hello", 0, 10, "hello"),

            doSimpleTest(ten, "ABC", 0, 10, "ABC"),
            doSimpleTest(ten, "ABC", 0, 5,  "ABC56789"),
            doSimpleTest(ten, "ABC", 4, 5,  "0123ABC9"),
            doSimpleTest(ten, "ABC", 5, 5,  "01234ABC")
        };

        for (int i = 0; i < a1.length; i++) {
            doCompositeTest(ten, a1[i], "yo", 0, 2);
        }

        {
            EPLChangeset ep1 = doSimpleTest(ten, "ABC", 0, 3, "ABC3456789");
            EPLChangeset ep2 = doSimpleTest(ten, "DEF", 7, 3, "0123456DEF");
            EPLChangeset ep3 = doSimpleTest(ten, "hello", 0, 10, "hello");

            System.out.println(ep1.toString() + ", " + ep2.toString() + ", " + ep3.toString());

            doCompositeTest(ten, ep1, "DEF", 7, 3);
            doCompositeTest(ten, ep2, "ABC", 0, 3);

            EPLChangeset ep12 = new EPLChangeset(EPLChangeset.composeSimpleEdit(ep1.toString(), "DEF", 7, 3, 10));
            System.out.println(ep12.toString());

            //doCompositeTest(ten, ep1, "hello", 0, 10);
            doCompositeTest(ten, ep12, "hello", 0, 10);
        }

        System.out.println("ran " + test_count + " tests, " + test_fails + " failed");
    }
}
