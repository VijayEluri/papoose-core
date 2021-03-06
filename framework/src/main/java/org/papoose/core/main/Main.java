/**
 *
 * Copyright 2010 (C) The original author or authors
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
package org.papoose.core.main;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.service.startlevel.StartLevel;

import org.papoose.core.PapooseConstants;
import org.papoose.core.PapooseFrameworkFactory;


/**
 * A simple class to instantiate a single instance of {@link Framework}.
 * <p/>
 * Note that this class does not use the Java&#8482; SE 6 <code>ServiceLoader</code>
 * class to create a FrameworkFactory instance.  This class is hardcoded to always
 * instantiate an instance of {@link PapooseFrameworkFactory}.
 * from the resource.
 *
 * @version $Revision: $ $Date: $
 */
public class Main
{

    private final static String CLASS_NAME = Main.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private static volatile Framework framework;

    /**
     * The property name used to specify whether the launcher should
     * install a shutdown hook.
     */
    public static final String SHUTDOWN_HOOK_PROP = "papoose.shutdown.hook";

    /**
     * The property name used to specify an URL to the configuration
     * property file to be used for the created the framework instance.
     */
    public static final String CONFIG_PROPERTIES_PROP = "papoose.config.properties";

    /**
     * The property name used to specify the initial start level value that is
     * assigned to a Bundle when it is first installed.
     * <p/>
     * See {@link StartLevel#setInitialBundleStartLevel(int)} for more details.
     */
    public static final String BUNDLE_START_LEVEL = "papoose.startlevel.bundle";

    /**
     * Construct an instance of {@link org.papoose.core.PapooseFramework}.
     * <p/>
     * The steps taken are
     * <ol>
     * <li>initialize the instance using properties from a properties file
     * located located by a URL in the systems property "papoose.config.properties".</li>
     * <li>install and optionally start bundles.  The lists of bundles to
     * install are found under the following property keys:
     * <dl>
     * <dt><tt>papoose.auto.install</tt></dt>
     * <dd>A space delimited list of bundle URLs to be installed when the
     * framework has been started.</dd>
     * <dt><tt>papoose.auto.install</tt>.N</dt>
     * <dd>A space delimited list of bundle URLs to be installed when the
     * framework has been started.  When N is a non-negative integer the
     * bundles that are installed have their start level set to that integer.</dd>
     * <dt><tt>papoose.auto.start</tt></dt>
     * <dd>A space delimited list of bundle URLs to be installed and started
     * when the framework has been started.</dd>
     * <dt><tt>papoose.auto.start</tt>.N</dt>
     * <dd>A space delimited list of bundle URLs to be installed and started
     * when the framework has been started.  When N is a non-negative integer
     * the bundles that are installed have their start level set to that
     * integer.</dd>
     * </dl>
     * </li>
     * <li>start the framework instance and wait for it to be stopped</li>
     * </ol>
     * <p/>
     * The framework instance can be interrupted but an orderly shutdown by
     * having a bundle call {@link Bundle#stop()} on the system bundle.
     *
     * @param args not used
     * @throws Exception is thrown if there is an error starting the framework
     */
    public static void main(String... args) throws Exception
    {
        Properties configuration = obtainConfigProps();

        configuration.put(PapooseConstants.PAPOOSE_BOOT_LEVEL_SERVICES, "start-level");
        configuration.put("start-level", "org.papoose.core.StartLevelImpl");

        FrameworkFactory factory = new PapooseFrameworkFactory();
        framework = factory.newFramework(configuration);

        boolean enableHook = Boolean.parseBoolean(configuration.getProperty(SHUTDOWN_HOOK_PROP, "false"));
        if (enableHook)
        {
            Runtime.getRuntime().addShutdownHook(new Thread("Papoose Shutdown Hook")
            {
                public void run()
                {
                    try
                    {
                        framework.stop();
                        framework.waitForStop(0);
                    }
                    catch (Exception e)
                    {
                        LOGGER.log(Level.WARNING, "Error stopping framework from JVM shutdown hook", e);
                    }
                }
            });
        }

        framework.init();

        processBundleRequests(configuration);

        framework.start();

        framework.waitForStop(0);

        System.exit(0);
    }

    /**
     * Process possible bundle install and start requests that may be present
     * in the configuration.
     *
     * @param configuration the configuration which may contain bundle install and start requests
     */
    private static void processBundleRequests(Properties configuration)
    {
        BundleContext context = framework.getBundleContext();
        StartLevel startLevel = (StartLevel) context.getService(context.getServiceReference(StartLevel.class.getName()));

        if (startLevel == null)
        {
            LOGGER.log(Level.WARNING, "Unable to find StartLevel service");
        }
        else if (configuration.contains(BUNDLE_START_LEVEL))
        {
            String initialBundleStartLevel = configuration.getProperty(BUNDLE_START_LEVEL);
            try
            {
                startLevel.setInitialBundleStartLevel(Integer.parseInt(initialBundleStartLevel));
            }
            catch (NumberFormatException nfe)
            {
                LOGGER.log(Level.WARNING, "Unable to parse initial bundle start level " + initialBundleStartLevel, nfe);
            }
        }

        List<Bundle> start = new ArrayList<Bundle>();
        for (String key : configuration.stringPropertyNames())
        {
            if (key.startsWith("papoose.auto.install") || key.startsWith("papoose.auto.start"))
            {
                String[] tokens = key.split("\\.");
                int level = -1;
                try
                {
                    if (tokens.length == 4) level = Integer.parseInt(tokens[3]);
                }
                catch (NumberFormatException nfe)
                {
                    LOGGER.log(Level.WARNING, "Unable to parse bundle start level " + key, nfe);
                }
                String[] urls = configuration.getProperty(key).split(" ");

                for (String url : urls)
                {
                    if (url.charAt(0) == '"') url = url.substring(1, url.length() - 1);

                    try
                    {
                        Bundle bundle = context.installBundle(url);
                        if (startLevel != null && level >= 0) startLevel.setBundleStartLevel(bundle, level);
                        if (key.startsWith("papoose.auto.start")) start.add(bundle);
                    }
                    catch (BundleException be)
                    {
                        LOGGER.log(Level.WARNING, "Unable to install " + url, be);
                    }
                }
            }
        }

        for (Bundle bundle : start)
        {
            try
            {
                bundle.start();
            }
            catch (BundleException be)
            {
                LOGGER.log(Level.WARNING, "Unable to start " + bundle.getLocation(), be);
            }
        }
    }

    /**
     * Load a properties file specified by the URL in the system property
     * <code>papoose.config.properties</code>.  If that property does not exist
     * or of there is a problem loading the file then an empty properties
     * object is returned.
     *
     * @return a properties file specified by the URL in the system property <code>papoose.config.properties</code>
     */
    private static Properties obtainConfigProps()
    {
        LOGGER.entering(CLASS_NAME, "obtainConfigProps");

        Properties properties = new Properties();

        String urlString = System.getProperty(CONFIG_PROPERTIES_PROP);
        try
        {
            properties.load(new URL(urlString).openStream());
        }
        catch (MalformedURLException mue)
        {
            LOGGER.log(Level.WARNING, "Bad properties URL " + urlString, mue);
        }
        catch (IOException ioe)
        {
            LOGGER.log(Level.WARNING, "Unable to open URL", ioe);
        }

        LOGGER.exiting(CLASS_NAME, "obtainConfigProps", properties);

        return properties;
    }
}
