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

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;

import junit.framework.TestCase;

import org.papoose.core.framework.spi.BundleStore;
import org.papoose.core.framework.spi.Store;
import org.papoose.core.framework.spi.ThreadPool;


/**
 * @version $Revision$ $Date$
 */
public class BundleClassLoaderTest extends TestCase
{
    public void testLoad()
    {
        Papoose papoose = new Papoose(
                new Store()
                {
                    public File getRoot()
                    {
                        return null;  //todo: consider this autogenerated code
                    }

                    public BundleStore allocateBundleStore(long bundleId)
                    {
                        return null;  //todo: consider this autogenerated code
                    }

                    public void removeBundleStore(long bundleId)
                    {
                        //todo: consider this autogenerated code
                    }
                },
                new ThreadPool()
                {
                    public boolean runInThread(Runnable runnable)
                    {
                        return false;  //todo: consider this autogenerated code
                    }
                },
                new Properties());

        String[] bootDelegates = new String[]{"com.foo.bar.", "com.bar"};
/*
    public BundleClassLoader(String name, URL[] bundleClasspath, ClassLoader parent,
                             Set<Wire> wires,
                             String[] bootDelegates,
                             Wire[] requiredBundles,
                             URL[] fragmentsClasspath,
                             String[] exportedPackages,
                             Set<ImportDescription> dynamicImports, Papoose papoose)
*/
        BundleClassLoader bundleClassLoader = new BundleClassLoader("bundle.1241", new URL[]{}, Thread.currentThread().getContextClassLoader(),
                                                                    Collections.EMPTY_SET,
                                                                    bootDelegates,
                                                                    new Wire[]{},
                                                                    new URL[]{},
                                                                    new String[]{},
                                                                    Collections.EMPTY_SET,
                                                                    papoose);

        try
        {
            Class test = bundleClassLoader.loadClass("com.foo.bar.Car");
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();  //todo: consider this autogenerated code
        }
    }
}