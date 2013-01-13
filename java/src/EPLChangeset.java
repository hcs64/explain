/*
 * Mostly based on the Changeset library from eitherpad-lite, which was copied
 * from the old Etherpad with some modifications to use it in node.js
 * Can be found in https://github.com/ether/pad/blob/master/infrastructure/ace/www/easysync2.js
 *
 * NOTE: No attributes support.
 *
 */ 

/*
 * Copyright 2009 Google Inc., 2011 Peter 'Pita' Martischka (Primary Technology Ltd)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Iterator;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class EPLChangeset {
    String original_string;

    static final Pattern headerRegex = Pattern.compile("Z:([0-9a-z]+)([><])([0-9a-z]+)");
    static final Pattern opRegex = Pattern.compile("((?:\\*[0-9a-z]+)*)(?:\\|([0-9a-z]+))?([-+=])([0-9a-z]+)|\\?");

    int oldLen;
    int newLen;
    String ops;
    String charBank;

    public EPLChangeset(String s) throws EPLChangesetException {
        original_string = s;
        unpack();
    }

    public EPLChangeset(int oldLen, int newLen, String newOps, String charBank) {
        this.oldLen = oldLen;
        this.newLen = newLen;
        this.ops = newOps;
        this.charBank = charBank;

        pack();
    }

    static public EPLChangeset identity(int len) {
        return new EPLChangeset(len, len, "", "");
    }

    static public EPLChangeset simpleEdit(String new_s, int pos, String whole_old_s, int removing) {
        int oldLen = whole_old_s.length();
        int new_s_len = new_s.length();
        int newLen = oldLen - removing + new_s_len;
        StringBuilder ops = new StringBuilder();

        if (pos > 0) {
            Operation keep = new Operation("", countNewlines(whole_old_s, 0, pos), '=', pos);
            ops.append(keep.toString());
        }

        if (removing > 0) {
            Operation remove = new Operation("", countNewlines(whole_old_s, pos, pos + removing), '-', removing);
            ops.append(remove.toString());
        }

        if (new_s_len > 0) {
            Operation add = new Operation("", countNewlines(new_s, 0, new_s_len), '+', new_s_len);
            ops.append(add.toString());
        }

        return new EPLChangeset(oldLen, newLen, ops.toString(), new_s);
    }

    // base 36
    public static int parseNum(String s, int start, int end) throws EPLChangesetException {
        String digits = s.substring(start, end);
        try {
            return Integer.parseInt(digits, 36);
        } catch (NumberFormatException e) {
            throw new EPLChangesetException("couldn't parse base36 number: " + e.toString());
        }
    }

    public static void appendNum(StringBuilder sb, int n) {
        sb.append(Integer.toString(n, 36));
    }

    public static void appendSignedNum(StringBuilder sb, int n) {
        if (n < 0) {
            sb.append('<');
            appendNum(sb, -n);
        } else {
            sb.append('>');
            appendNum(sb, n);
        }
    }

    private void unpack() throws EPLChangesetException {
        final String cs = original_string;
        Matcher m = headerRegex.matcher(cs);

        if (!m.find() || m.start(0) == m.end(0)) {
            throw new EPLChangesetException("header not matched in '"+cs+"'");
        }

        oldLen = parseNum(cs, m.start(1), m.end(1));
        int changeSign = cs.charAt(m.start(2)) == '>' ? 1 : -1;
        int changeMag = parseNum(cs, m.start(3), m.end(3));
        newLen = oldLen + changeSign * changeMag;
        int opsStart = m.end(0);
        int opsEnd = cs.indexOf("$");
        if (opsEnd < 0) opsEnd = cs.length();
        ops = cs.substring(opsStart, opsEnd);
        charBank = cs.substring(opsEnd+1);
    }

    private void pack() {
        StringBuilder cs = new StringBuilder();

        cs.append("Z:");
        appendNum(cs, oldLen);
        appendSignedNum(cs, newLen-oldLen);
        cs.append(ops);
        if (charBank != null && charBank.length() > 0) {
            cs.append('$');
            cs.append(charBank);
        }

        original_string = cs.toString();
    }

    // an immutable object representing an edit operation
    static class Operation {
        public final String attribs;
        public final int lines;
        public final char opcode;
        public final int chars;

        public Operation(String attribs, int lines, char opcode, int chars) {
            this.attribs = attribs;
            this.lines = lines;
            this.opcode = opcode;
            this.chars = chars;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();

            sb.append(attribs);
            if (lines > 0) {
                sb.append('|');
                appendNum(sb, lines);
            }
            sb.append(opcode);
            appendNum(sb, chars);

            return sb.toString();
        }

        public String explain(String bank, int bank_cur) {
            switch (this.opcode) {
                case '=':
                    return "keep " + chars;
                case '+':
                    return "add " + chars + ' ' + bank.substring(bank_cur, bank_cur+chars);
                case '-':
                    return "remove " + chars;
                default:
                    return "invalid op " + this.opcode;
            }
        }
    }

    static class MutableOperation {
        public String attribs;
        public int lines;
        public char opcode;
        public int chars;

        public MutableOperation(String attribs, int lines, char opcode, int chars) {
            this.attribs = attribs;
            this.lines = lines;
            this.opcode = opcode;
            this.chars = chars;
        }

        public MutableOperation(Operation o) {
            gets(o);
        }

        public void gets(Operation o) {
            attribs = o.attribs;
            lines = o.lines;
            opcode = o.opcode;
            chars = o.chars;
        }

        public MutableOperation() {
            invalidate();
        }

        public void invalidate() {
            opcode = ' ';
        }

        boolean isValid() {
            return (opcode == '+' || opcode == '-' || opcode == '=');
        }

        public Operation toImmutable() {
            if (!isValid()) {
                return null;
            }
            return new Operation(attribs, lines, opcode, chars);
        }
    }

    static class OpIterator implements Iterator {
        private String opstring;
        private int curIndex;

        Matcher m;

        Operation regexResult;

        private Operation nextRegexMatch() throws EPLChangesetException {
            if (m.find(curIndex)) {
                curIndex = m.end(0);

                String attribs = opstring.substring(m.start(1), m.end(1));
                int lines = 0;
                try {
                    if (m.start(2) != m.end(2)) {
                        lines = parseNum(opstring, m.start(2), m.end(2));
                    }
                } catch (EPLChangesetException e) {}

                char opcode = opstring.charAt(m.start(3));
                int chars = parseNum(opstring, m.start(4), m.end(4));

                return new Operation(attribs, lines, opcode, chars);
            }
            return null;
        }

        public OpIterator(String opstring) {
            this.opstring = opstring;

            curIndex = 0;

            m = opRegex.matcher(opstring);

            regexResult = null;
            try {
                regexResult = nextRegexMatch();
            } catch (EPLChangesetException e) {}
        }

        public OpIterator(String opstring, int offset) {
            this.opstring = opstring;
            
            curIndex = offset;

            m = opRegex.matcher(opstring);

            regexResult = null;
            try  {
                regexResult = nextRegexMatch();
            } catch (EPLChangesetException e) {}
        }

        public boolean hasNext() {
            return (regexResult != null);
        }

        public Object next() {
            Operation o = regexResult;
            
            regexResult = null;
            try {
                regexResult = nextRegexMatch();
            } catch (EPLChangesetException e) { }

            return o;
        }

        public void remove() throws UnsupportedOperationException {}

    }

    private OpIterator opIterator() {
        return new OpIterator(ops);
    }

    // Ignoring all line and attribute info for now.
    public String applyToText(String s) throws EPLChangesetException {
        if (s.length() != oldLen) {
            throw new EPLChangesetException("applying "+original_string+" to length " + s.length() + ", should be " + oldLen);
        }

        StringBuilder assem = new StringBuilder(newLen);

        int s_cur = 0;
        int bank_cur = 0;

        for (OpIterator oi = opIterator(); oi.hasNext(); ) {
            Operation o = (Operation) oi.next();

            switch (o.opcode) {
                case '=':
                    assem.append(s.subSequence(s_cur, s_cur + o.chars));
                    s_cur += o.chars;
                    break;
                case '-':
                    s_cur += o.chars;
                    break;
                case '+':
                    assem.append(charBank.subSequence(bank_cur, bank_cur + o.chars));
                    bank_cur += o.chars;
                    break;
            }
        }

        assem.append(s.subSequence(s_cur, s.length()));

        return assem.toString();
    }


    /**
     * compose two Changesets
     * @param cs1 {Changeset} first Changeset
     * @param cs2 {Changeset} second Changeset
     * @param pool {AtribsPool} Attribs pool
     */
    public static EPLChangeset compose(EPLChangeset cs1, EPLChangeset cs2) throws EPLChangesetException { //, pool) {
        int len1 = cs1.oldLen;
        int len2 = cs1.newLen;
        int len3 = cs2.newLen;

        if (len2 != cs2.oldLen) {
            throw new EPLChangesetException("mismatched composition");
        }

        final StringIterator bankIter1 = new StringIterator(cs1.charBank);
        final StringIterator bankIter2 = new StringIterator(cs2.charBank);
        final StringBuilder bankAssem = new StringBuilder();

        Zipper z = new Zipper(new Zipper.F2() {
            public Operation func(MutableOperation op1, MutableOperation op2) throws EPLChangesetException {
                Operation opOut = null;
                char op1code = op1.opcode;
                char op2code = op2.opcode;

                if (op1code == '+' && op2code == '-') {
                    // op2 consumes chars banked by op1
                    bankIter1.skip(Math.min(op1.chars, op2.chars));
                }

                //opOut = slicerZipperFunc(op1, op2, pool);
                if (op1.opcode == '-') {
                    // op1 is removal, preserve
                    opOut = op1.toImmutable();
                    op1.invalidate();
                } else if (!op1.isValid()) {
                    // no op1, use op2 verbatim
                    opOut = op2.toImmutable();
                    op2.invalidate();
                } else {
                    switch (op2.opcode) {
                    case '-':
                        if (op2.chars <= op1.chars) {
                            // delete or delete part
                            if (op1code == '=') {
                                opOut = new Operation("", op2.lines, '-', op2.chars);
                            }
                            op1.chars -= op2.chars;
                            op1.chars -= op2.lines;
                            op2.invalidate();
                            if (op1.chars == 0) {
                                op1.invalidate();
                            }
                        } else { // op2.chars > op1.chars
                            // delete and keep deleting
                            if (op2.opcode == '=') {
                                opOut = new Operation("", op1.lines, '-', op1.chars);
                            }
                            op2.chars -= op1.chars;
                            op2.lines -= op1.lines;
                            op1.invalidate();
                        }
                        break;
                    case '+':
                        // op2 is insertion, preserve
                        opOut = op2.toImmutable();
                        break;
                    case '=':
                        if (op2.chars <= op1.chars) {
                            // keep or keep part
                            // do the operation from op1 for the chars/lines counted by op2
                            //TODO: attrib stuff needed here
                            opOut = new Operation("", op2.lines, op1.opcode, op2.chars);
                            op1.chars -= op2.chars;
                            op1.lines -= op2.lines;
                            op2.invalidate();

                            if (op1.chars == 0) {
                                op1.invalidate();
                            }
                        } else { // op2.chars > op1.chars
                            // keep and keep on keeping on
                            //TODO: attrib stuff needed here
                            opOut = new Operation("", op1.lines, op1.opcode, op1.chars);
                            op2.chars -= op1.chars;
                            op2.lines -= op1.lines;
                        }
                        break;
                    default: // op2 is invalid
                        // no op2, use op1 verbatim
                        opOut = op1.toImmutable();
                        op1.invalidate();
                        break;
                    }
                }

                if (opOut != null && opOut.opcode == '+') {
                    // if opOut is insertion, where did the chars come from?
                    if (op2code == '+') {
                        // came from op2
                        bankAssem.append(bankIter2.take(opOut.chars));
                    } else {
                        // came from op1
                        bankAssem.append(bankIter1.take(opOut.chars));
                    }
                }

                return opOut;
            }
        });

        String newOps = z.apply(cs1.ops, 0, cs2.ops, 0);

        return new EPLChangeset(len1, len3, newOps, bankAssem.toString());
    }

    public String toString() {
        return original_string;
    }

    public String explain() {
        StringBuilder sb = new StringBuilder(original_string);
        int bank_cur = 0;

        for (OpIterator oi = opIterator(); oi.hasNext(); ) {
            Operation o = (Operation) oi.next();
            sb.append('\n');
            sb.append(o.explain(charBank, bank_cur));

            if (o.opcode == '+') {
                bank_cur += o.chars;
            }
        }

        return sb.toString();
    }

    /* ==================== Util Classes ======================= */

    // A custom made String Iterator
    static class StringIterator {
        private final String str;
        private int curIndex;

        public StringIterator(String str) {
            this.str = str;
            this.curIndex = 0;
        }

        private void assertRemaining(int n) throws EPLChangesetException {
            if (n <= remaining()) {
                // we're happy
            } else {
                throw new EPLChangesetException("!(" + n + " <= " + remaining() + ")");
            }
        }

        public String peek(int n) throws EPLChangesetException {
            assertRemaining(n);
            String s = str.substring(curIndex, n);
            return s;
        }

        public String take(int n) throws EPLChangesetException {
            String s = peek(n);
            curIndex += n;
            return s;
        }

        public void skip(int n) throws EPLChangesetException {
            assertRemaining(n);
            curIndex += n;
        }

        public int remaining() throws EPLChangesetException {
            return str.length() - curIndex;
        }
    }

    static interface OpAssembler {
        public void append(Operation o);
        public void clear();
        public String toString();
        public void endDocument();
    }

    static class OpAssemblerImpl implements OpAssembler {
        private StringBuilder sb;

        public OpAssemblerImpl() {
            clear();
        }

        public void append(Operation o) {
            sb.append(o.toString());
        }

        public void clear() {
            sb = new StringBuilder();
        }

        public void endDocument() {
            // nothing to do
        }

        public String toString() {
            return sb.toString();
        }
    }

    // This assembler can be used in production; it efficiently
    // merges consecutive operations that are mergeable, ignores
    // no-ops, and drops final pure "keeps".  It does not re-order
    // operations.

    static class MergingOpAssembler extends OpAssemblerImpl {
        private MutableOperation bufOp;

        // If we get, for example, insertions [xxx\n,yyy], those don't merge,
        // but if we get [xxx\n,yyy,zzz\n], that merges to [xxx\nyyyzzz\n].
        // This variable stores the length of yyy and any other newline-less
        // ops immediately after it.
        private int bufOpAdditionalCharsAfterNewline;

        public MergingOpAssembler() {
            bufOp = new MutableOperation();
            bufOpAdditionalCharsAfterNewline = 0;
        }

        private void flush(boolean isEndDocument) {
            if (bufOp.isValid()) {
                if (isEndDocument && bufOp.opcode == '=' && bufOp.attribs.length() == 0) {
                    // final merged keep, leave it implicit
                } else {
                    super.append(bufOp.toImmutable());

                    if (bufOpAdditionalCharsAfterNewline > 0) {
                        // the buffered op ends on a newline but we're got some more chars,
                        // by flushing we need to add another non-multi-line op
                        int lines = 0;
                        super.append(new Operation(bufOp.attribs, lines, bufOp.opcode, bufOpAdditionalCharsAfterNewline));
                        bufOpAdditionalCharsAfterNewline = 0;
                    }
                }

                bufOp.invalidate();
            }
        }

        @Override
        public void append(Operation op) {
            if (op.chars > 0) {
                if (bufOp.isValid() && bufOp.opcode == op.opcode && bufOp.attribs.equals(op.attribs)) {
                    // should be able to merge
                    if (op.lines > 0) {
                        // bufOp and additional chars are all mergeable into a multi-line op
                        bufOp.chars += bufOpAdditionalCharsAfterNewline + op.chars;
                        bufOp.lines += op.lines;
                        bufOpAdditionalCharsAfterNewline = 0;
                    } else if (bufOp.lines == 0) {
                        // both bufOp and op are in-line
                        bufOp.chars += op.chars;
                    } else {
                        // append in-line text to multi-line bufOp
                        bufOpAdditionalCharsAfterNewline += op.chars;
                    }
                } else {
                    // can't merge, flush buffer and buffer the new op
                    flush(false);
                    bufOp.gets(op);
                }
            }
        }

        @Override
        public void endDocument() {
            flush(true);
        }

        @Override
        public String toString() {
            flush(false);
            return super.toString();
        }

        @Override
        public void clear() {
            super.clear();
            bufOp.invalidate();
        }
    }

    // Like opAssembler but able to produce conforming exportss
    // from slightly looser input, at the cost of speed.
    // Specifically:
    // - merges consecutive operations that can be merged
    // - strips final "="
    // - ignores 0-length changes
    // - reorders consecutive + and - (which margingOpAssembler doesn't do)

    static class SmartOpAssembler implements OpAssembler {
        MergingOpAssembler minusAssem;
        MergingOpAssembler plusAssem;
        MergingOpAssembler keepAssem;
        StringBuilder assem;

        private char lastOpcode;
        private int lengthChange;

        public SmartOpAssembler() {
            minusAssem = new MergingOpAssembler();
            plusAssem = new MergingOpAssembler();
            keepAssem = new MergingOpAssembler();
            assem = new StringBuilder();
        }

        private void flushKeeps() {
            assem.append(keepAssem.toString());
            keepAssem.clear();
        }

        private void flushPlusMinus() {
            assem.append(minusAssem.toString());
            minusAssem.clear();
            assem.append(plusAssem.toString());
            plusAssem.clear();
        }

        @Override
        public void append(Operation op) {
            if (op == null || op.chars == 0) { return; }

            switch (op.opcode) {
                case '-':
                    if (lastOpcode == '=') {
                        flushKeeps();
                    }
                    minusAssem.append(op);
                    lengthChange -= op.chars;
                    break;
                case '+':
                    if (lastOpcode == '=') {
                        flushKeeps();
                    }
                    plusAssem.append(op);
                    lengthChange += op.chars;
                    break;
                case '=':
                    if (lastOpcode != '=') {
                        flushPlusMinus();
                    }
                    keepAssem.append(op);
                    break;
            }

            lastOpcode = op.opcode;
        }

        public void appendOpWithText(char opcode, String text) {//, String attribs, String pool) {
            int lastNewlinePos = text.lastIndexOf('\n');
            String attribs = "";

            if (lastNewlinePos < 0) {
                int chars = text.length();
                int lines = 0;
                append(new Operation(attribs, lines, opcode, chars));
            } else {
                // build a multiline operation
                int chars = lastNewlinePos + 1;
                int lines = countNewlines(text, 0, text.length());
                append(new Operation(attribs, lines, opcode, chars));

                // take what's left for a in-line operation
                chars = text.length() - chars;
                lines = 0;
                append(new Operation(attribs, lines, opcode, chars));
            }
        }

        @Override
        public String toString() {
            flushPlusMinus();
            flushKeeps();
            return assem.toString();
        }

        @Override
        public void clear() {
            minusAssem.clear();
            plusAssem.clear();
            keepAssem.clear();
            assem = new StringBuilder();
            lengthChange = 0;
        }

        @Override
        public void endDocument() {
                keepAssem.endDocument();
        }

        public int getLengthChange() {
            return lengthChange;
        }
    }

    static class Zipper {
        public interface F2 {
            Operation func(MutableOperation op1, MutableOperation op2) throws EPLChangesetException;
        }

        private final F2 func_interface;

        public Zipper (F2 func_interface) {
            this.func_interface = func_interface;
        }

        public String apply(String in1, int idx1, String in2, int idx2) throws EPLChangesetException {
            OpIterator iter1 = new OpIterator(in1, idx1);
            OpIterator iter2 = new OpIterator(in2, idx2);
            SmartOpAssembler assem = new SmartOpAssembler();

            MutableOperation op1 = new MutableOperation();
            MutableOperation op2 = new MutableOperation();

            while (op1.isValid() || iter1.hasNext() || op2.isValid() || iter2.hasNext()) {
                if (!op1.isValid() && iter1.hasNext()) op1.gets((Operation)iter1.next());
                if (!op2.isValid() && iter2.hasNext()) op2.gets((Operation)iter2.next());

                Operation opOut = func_interface.func(op1, op2);

                if (opOut != null) {
                    assem.append(opOut);
                }
            }

            assem.endDocument();
            return assem.toString();
        }
    }

    // end is noninclusive
    public static int countNewlines(String s, int start, int end) {
        int i = start;
        int c = 0;
        while (i < end && (i = s.indexOf('\n', i)) >= 0) {
            if (i >= end) break;
            c++;    // count
            i++;    // search just past the one we've just found
        }

        return c;
    }

}
