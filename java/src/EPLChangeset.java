/*
 * Mostly based on the Changeset library from eitherpad-lite, which was copied
 * from the old Etherpad with some modificationsto use it in node.js
 * Can be found in https://github.com/ether/pad/blob/master/infrastructure/ace/www/easysync2.js
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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class EPLChangeset {
    final String original_string;

    static final Pattern headerRegex = Pattern.compile("Z:([0-9a-z]+)([><])([0-9a-z]+)|");

    int oldLen;
    int newLen;
    String ops;
    String charbank;

    public EPLChangeset(String s) throws EPLChangesetException {
        original_string = s;
        unpack();

        System.out.println("oldLen   = " + oldLen);
        System.out.println("newLen   = " + newLen);
        System.out.println("ops      = " + ops);
        System.out.println("charbank = " + charbank);
    }

    // base 36
    public int parseNum(int start, int end) throws EPLChangesetException {
        String digits = original_string.substring(start, end);
        try {
            return Integer.parseInt(digits, 36);
        } catch (NumberFormatException e) {
            throw new EPLChangesetException("couldn't parse base36 number: " + e.toString());
        }
    }

    private void unpack() throws EPLChangesetException {
        final String cs = original_string;
        Matcher m = headerRegex.matcher(cs);

        if (!m.find()) {
            throw new EPLChangesetException("header not matched in '"+cs+"'");
        }

        oldLen = parseNum(m.start(1), m.end(1));
        int changeSign = cs.charAt(m.start(2)) == '>' ? 1 : -1;
        int changeMag = parseNum(m.start(3), m.end(3));
        newLen = oldLen + changeSign * changeMag;
        int opsStart = m.end(0);
        int opsEnd = cs.indexOf("$");
        if (opsEnd < 0) opsEnd = cs.length();
        ops = cs.substring(opsStart, opsEnd);
        charbank = cs.substring(opsEnd);
    }
};
