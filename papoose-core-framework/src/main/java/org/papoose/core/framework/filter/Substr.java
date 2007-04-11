/**
 *
 * Copyright 2007 (C) The original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.papoose.core.framework.filter;

import java.util.Collection;
import java.util.Dictionary;


/**
 * @version $Revision$ $Date$
 */
class Substr implements Expr
{
    private final String attribute;
    private final String[] values;
    private transient volatile String string;

    public Substr(String attribute, String[] values)
    {
        if (attribute == null) throw new IllegalArgumentException("attribute is null");
        if (values == null) throw new IllegalArgumentException("values is null");
        if (values.length < 2) throw new IllegalArgumentException("values does not have the proper number of elements");

        this.attribute = attribute;
        this.values = values;
    }

    public boolean match(Dictionary<String, Object> dictionary)
    {
        Object test = dictionary.get(attribute);
        if (test instanceof String) return match((String) test);
        else if (test instanceof String[])
        {
            for (String element : (String[]) test) if (element != null && match(element)) return true;
            return false;
        }
        else if (test instanceof Collection)
        {
            for (Object element : (Collection) test)
            {
                if (!(element instanceof String)) return false;
                if (match((String) element)) return true;
            }
            return false;
        }
        else return false;
    }

    protected boolean match(String test)
    {
        if (!values[0].regionMatches(0, test, 0, values[0].length())) return false;

        int pointer = values[0].length();

        done:
        for (int i = 1; i < values.length - 1; i++)
        {
            int length = values[i].length();
            int limit = test.length() - length;
            while (pointer <= limit)
            {
                if (values[i].regionMatches(0, test, pointer++, length))
                {
                    pointer += values[i].length() - 1;
                    continue done;
                }
            }
            return false;
        }

        return test.substring(pointer).endsWith(values[values.length - 1]);
    }

    public String toString()
    {
        if (string == null)
        {
            StringBuilder builder = new StringBuilder();

            builder.append('(');
            builder.append(attribute);
            builder.append('=');
            if (values[0].length() != 0) builder.append(values[0]);
            builder.append('*');
            for (int i = 1; i < values.length - 1; i++)
            {
                builder.append(escape(values[i]));
                builder.append('*');
            }
            if (values[values.length - 1].length() != 0) builder.append(escape(values[values.length - 1]));
            builder.append(')');

            string = builder.toString();
        }
        return string;
    }

    private StringBuilder escape(String string)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < string.length(); i++)
        {
            char c = string.charAt(i);
            switch (c)
            {
                case '\\' :
                {
                    builder.append("\\\\");
                    break;
                }
                case '(' :
                {
                    builder.append("\\(");
                    break;
                }
                case '*' :
                {
                    builder.append("\\*");
                    break;
                }
                case ')' :
                {
                    builder.append("\\)");
                    break;
                }
                default:
                    builder.append(c);
            }
        }

        return builder;
    }
}
