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
package org.papoose.core.framework;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Version;


/**
 * @version $Revision$ $Date$
 */
public class ImportDescription
{
    public final static VersionRange DEFAULT_VERSION_RANGE = new VersionRange(new Version(0, 0, 0), null, true, false);
    private final List<String> packageNames;
    private final Map<String, Object> parameters;
    private Resolution resolution;
    private transient volatile String string;

    public ImportDescription(List<String> packageNames, Map<String, Object> parameters)
    {
        assert packageNames != null;
        assert parameters != null;

        this.packageNames = Collections.unmodifiableList(packageNames);
        this.parameters = Collections.unmodifiableMap(parameters);
    }

    public List<String> getPackageNames()
    {
        return packageNames;
    }

    public Map<String, Object> getParameters()
    {
        return parameters;
    }


    public Resolution getResolution()
    {
        return resolution;
    }

    void setResolution(Resolution resolution)
    {
        this.resolution = resolution;
    }

    public String toString()
    {
        if (string == null)
        {
            StringBuilder builder = new StringBuilder();

            for (String pkg : packageNames)
            {
                if (builder.length() > 0) builder.append(";");
                builder.append(pkg);
            }
            builder.append(";resolution=");
            builder.append(resolution);
            if (!parameters.isEmpty())
            {
                for (String key : parameters.keySet())
                {
                    builder.append(";");
                    builder.append(key);
                    builder.append("=");
                    builder.append(parameters.get(key));
                }
            }

            string = builder.toString();
        }
        return string;
    }
}
