/*
 * Mostly based on the Changeset library from eitherpad-lite, which was copied
 * from the old Etherpad with some modificationsto use it in node.js
 * Can be found in https://github.com/ether/pad/blob/master/infrastructure/ace/www/easysync2.js
 *
 * Just doing the easy stuff (applying changesets) now.
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

import java.util.Enumeration;
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

    // base 36
    public static int parseNum(String s, int start, int end) throws EPLChangesetException {
        String digits = s.substring(start, end);
        try {
            return Integer.parseInt(digits, 36);
        } catch (NumberFormatException e) {
            throw new EPLChangesetException("couldn't parse base36 number: " + e.toString());
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
    }

    class OpEnumeration implements Enumeration {
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

        public OpEnumeration(String opstring) {
            this.opstring = opstring;

            curIndex = 0;
            prevIndex = 0;

            m = opRegex.matcher(opstring);

            regexResult = null;
            try {
                regexResult = nextRegexMatch();
            } catch (EPLChangesetException e) {}
        }

        public boolean hasMoreElements() {
            return (regexResult != null);
        }

        public Object nextElement() {
            Operation o = regexResult;
            
            regexResult = null;
            try {
                regexResult = nextRegexMatch();
            } catch (EPLChangesetException e) { }

            return o;
        }
    }

    private OpEnumeration opEnumeration() {
        return new OpEnumeration(ops);
    }

    public String applyToText(String s) throws EPLChangesetException {
        if (s.length() != oldLen) {
            throw new EPLChangesetException("applying "+original_string+" to length " + s.length() + ", should be " + oldLen);
        }

        StringBuilder assem = new StringBuilder(newLen);

        int s_cur = 0;
        int bank_cur = 0;

        for (OpEnumeration oe = opEnumeration(); oe.hasMoreElements(); ) {
            Operation o = (Operation) oe.nextElement();

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
};
