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
    final String original_string;

    static final Pattern headerRegex = Pattern.compile("Z:([0-9a-z]+)([><])([0-9a-z]+)");
    static final Pattern opRegex = Pattern.compile("((?:\\*[0-9a-z]+)*)(?:\\|([0-9a-z]+))?([-+=])([0-9a-z]+)|\\?");

    int oldLen;
    int newLen;
    String ops;
    String charbank;

    public EPLChangeset(String s) throws EPLChangesetException {
        original_string = s;
        unpack();

    }

    public static String makeSimpleDeletion(int pos, int replacing, int oldLen) {
        return makeSimpleEdit("", pos, replacing, oldLen);
    }

    public static String makeSimpleInsertion(String replacement, int pos, int oldLen) {
        return makeSimpleEdit(replacement, pos, 0, oldLen);
    }

    public static String makeSimpleEdit(String replacement, int pos, int replacing, int oldLen) {
        StringBuilder cmd = new StringBuilder();

        int repLen = replacement.length();
        StringBuilder bank = new StringBuilder(repLen);

        int newLen = oldLen - replacing + repLen;

        appendHeader(cmd, oldLen, newLen);

        if (pos > 0) {
            appendOperation(cmd, '=', 0, pos);
        }

        appendSimpleEdit(cmd, bank, replacement, replacing);
        if (repLen > 0) {
            cmd.append('$');
            cmd.append(bank);
        }

        return cmd.toString();
    }

    public static void appendSimpleEdit(StringBuilder cmd, StringBuilder bank, String replacement, int replacing) {
        int repLen = replacement.length();

        if (replacing > 0) {
            appendOperation(cmd, '-', 0, replacing);
        }
        if (repLen > 0) {
            appendOperation(cmd, '+', 0, repLen);
            bank.append(replacement);
        }
    }

    // An attempt at composition for just adding a simple edit (one contiguous change) to a possibly complex one
    public static String composeSimpleEdit(String oldcmd, String replacement, int pos, int replacing, int oldLen) throws EPLChangesetException {
        StringBuilder cmd = new StringBuilder();
        StringBuilder bank = new StringBuilder();

        int repLen = replacement.length();

        int newLen = oldLen - replacing + repLen;

        EPLChangeset oldcs = new EPLChangeset(oldcmd);

        if (oldcs.newLen != oldLen) {
            throw new EPLChangesetException("bad composition attempted, "+oldcmd+"'s newLen is not "+oldLen);
        }

        appendHeader(cmd, oldcs.oldLen, newLen);

        int bank_cur = 0;
        int cur = 0;

        boolean edit_emitted = false;

        int edit_start = pos;
        int edit_end   = pos + replacing - 1;

        for (OpIterator oi = oldcs.opIterator(); oi.hasNext(); ) {
            Operation o = (Operation) oi.next();

            if (cur > edit_end && !edit_emitted) {
                appendSimpleEdit(cmd, bank, replacement, replacing);
                edit_emitted = true;
            }

            if (o.opcode == '-') {
                // removed characters have no impact on the current state
                // replicate verbatim

                appendOperation(cmd, '-', o.lines, o.chars);
                continue;
            }

            int op_start = cur;
            int op_end   = cur + o.chars - 1;

            int max_of_min = Math.max(op_start, edit_start);
            int min_of_max = Math.min(op_end,   edit_end);

            cur += o.chars;

            if (min_of_max < max_of_min) {
                // there is no overlap
                appendOperation(cmd, o.opcode, o.lines, o.chars);

                if (o.opcode == '+') {
                    bank.append(oldcs.charbank.substring(bank_cur, bank_cur + o.chars));
                    bank_cur += o.chars;
                }

                continue;
            }

            // there is some kind of overlap
            if (op_start < edit_start) {
                // operation starts before edit starts
                if (op_end <= edit_end) {
                    // only start of op remains, shorten
                    int overlap = op_end - edit_start + 1;
                    int shortlen = o.chars - overlap;

                    // emit short op
                    // TODO: lines?
                    appendOperation(cmd, o.opcode, 0, shortlen);
                    if (o.opcode == '+') {
                        // handle bank

                        bank.append(oldcs.charbank.substring(bank_cur, bank_cur + shortlen));
                        bank_cur += o.chars;
                    }

                    // shorten edit as well
                    if (o.opcode == '+') {
                        replacing -= overlap;
                    }
                } else  {
                    // edit is entirely contained in this op,
                    // op must be split
                    int shortlen;

                    // emit start of op
                    shortlen = edit_start - op_start;
                    appendOperation(cmd, o.opcode, 0, shortlen);
                    if (o.opcode == '+') {
                        // handle bank
                        bank.append(oldcs.charbank.substring(bank_cur, bank_cur + shortlen));
                        bank_cur += shortlen + replacing;
                    }

                    // emit edit (we're seeing the end of the edit)
                    appendSimpleEdit(cmd, bank, replacement, replacing);
                    edit_emitted = true;

                    // emit end of op
                    shortlen = op_end - edit_end;
                    appendOperation(cmd, o.opcode, 0, shortlen);
                    if (o.opcode == '+') {
                        // handle bank
                        bank.append(oldcs.charbank.substring(bank_cur, bank_cur + shortlen));
                        bank_cur += shortlen;
                    }
                }
            } else {
                // operation starts during edit

                if (op_end <= edit_end) {
                    // operation is entirely contained in edit,
                    // op must be discarded

                    if (o.opcode == '+') {
                        // handle bank
                        bank_cur += o.chars;
                    }

                    if (o.opcode == '+') {
                        // shorten edit as we effectively remove these
                        replacing -= o.chars;
                    }
                } else {
                    // only end of op remains, shorten
                    int overlap = edit_end - op_start + 1;
                    int shortlen = o.chars - overlap;

                    // emit edit (we're seeing the end of the edit)
                    if (o.opcode == '+') {
                        replacing -= overlap;
                    }
                    appendSimpleEdit(cmd, bank, replacement, replacing);
                    edit_emitted = true;

                    // emit short op
                    appendOperation(cmd, o.opcode, 0, shortlen);

                    if (o.opcode == '+') {
                        // handle bank
                        bank_cur += overlap;
                        bank.append(oldcs.charbank.substring(bank_cur, bank_cur+shortlen));
                        bank_cur += shortlen;
                    }
                }
            }
        }

        if (!edit_emitted) {
            if (cur < pos) {
                appendOperation(cmd, '=', 0, pos-cur);
            }
            appendSimpleEdit(cmd, bank, replacement, replacing);
        }

        if (bank.length() > 0) {
            cmd.append('$');
            cmd.append(bank);
        }

        return cmd.toString();
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

    public static void appendHeader(StringBuilder sb, int oldLen, int newLen) {
        int changeMag = newLen - oldLen;
        char dir = '>';
        if (newLen < oldLen) {
            dir = '<';
            changeMag = oldLen - newLen;
        }

        sb.append("Z:");
        appendNum(sb,oldLen);
        sb.append(dir);
        appendNum(sb,changeMag);
    }

    public static void appendOperation(StringBuilder sb, char op, int lines, int len) {
        if (lines > 0) {
            sb.append('|');
            appendNum(sb, lines);
        }
        sb.append(op);
        appendNum(sb, len);
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
        charbank = cs.substring(opsEnd+1);
    }

    class Operation {
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

        public String toString(String bank, int bank_cur) {
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

    class OpIterator implements Iterator {
        private String opstring;
        private int prevIndex;
        private int curIndex;

        Matcher m;

        Operation regexResult;

        private Operation nextRegexMatch() throws EPLChangesetException {
            if (m.find(curIndex)) {
                prevIndex = curIndex;
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
            prevIndex = 0;

            m = opRegex.matcher(opstring);

            regexResult = null;
            try {
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
                    assem.append(charbank.subSequence(bank_cur, bank_cur + o.chars));
                    bank_cur += o.chars;
                    break;
            }
        }

        assem.append(s.subSequence(s_cur, s.length()));

        return assem.toString();
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
            sb.append(o.toString(charbank, bank_cur));

            if (o.opcode == '+') {
                bank_cur += o.chars;
            }
        }

        return sb.toString();
    }
};
