/*
 * Copyright 2024 the original author, Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.newproxy;

/**
 * Utility of {@link String}.
 *
 * @author Lam Tong
 * @version 1.0.0
 * @since 1.0.0
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * Utility method to take a string and convert it to normal a string with the first character in upper case.
     * Thus, "foobar" becomes "Foobar" and "x" becomes "X".
     *
     * @param s the string to be capitalized
     * @return the capitalized version of string
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        char[] chars = s.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

}
