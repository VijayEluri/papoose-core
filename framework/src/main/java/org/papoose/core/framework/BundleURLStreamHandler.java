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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;


/**
 * bundle://generation@framework:bundle/file
 *
 * @version $Revision$ $Date$
 */
public class BundleURLStreamHandler extends URLStreamHandler
{
    protected URLConnection openConnection(URL url) throws IOException
    {
        return allocateConnection(url);
    }

    @SuppressWarnings({ "EmptyCatchBlock" })
    public static URLConnection allocateConnection(URL url) throws IOException
    {
        try
        {
            Integer frameworkId = Integer.parseInt(url.getUserInfo());

            if (frameworkId < 0) throw new MalformedURLException("Invalid format");

            Papoose framework = Papoose.getFramework(frameworkId);

            if (framework == null) throw new MalformedURLException("Invalid format");

            int bundleId = Integer.parseInt(url.getHost());

            if (bundleId < 0) throw new MalformedURLException("Invalid format");

            int generation = url.getPort();

            return new BundleURLConnection(url, framework.getBundleManager(), bundleId, generation);
        }
        catch (NumberFormatException fallThrough)
        {
        }

        throw new MalformedURLException("Invalid format");
    }
}