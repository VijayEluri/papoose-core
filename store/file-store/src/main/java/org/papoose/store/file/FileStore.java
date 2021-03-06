/**
 *
 * Copyright 2008-2009 (C) The original author or authors
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
package org.papoose.store.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import org.papoose.core.FatalError;
import org.papoose.core.Papoose;
import org.papoose.core.PapooseException;
import org.papoose.core.spi.ArchiveStore;
import org.papoose.core.spi.BundleStore;
import org.papoose.core.spi.Store;
import org.papoose.core.util.FileUtils;
import org.papoose.core.util.ToStringCreator;


/**
 *
 */
public class FileStore implements Store
{
    public final static String FILESTORE_VERSION_KEY = "org.papoose.store.file.version";
    public final static String FILESTORE_VERSION = "1.0";
    private final static String CLASS_NAME = FileStore.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final static String PROPERTIES_FILE = "store.properties";
    private final static String GENERATION_KEY = "generation.";
    private final static String SYSTEM_DIR = "system";
    private final static String BUNDLES_DIR = "bundles";
    private final static String GENERATIONS_DIR = "generations";
    private final Properties properties = new Properties();
    private final File root;

    public FileStore(File root)
    {
        LOGGER.entering(CLASS_NAME, "FileStore");

        if (root == null) throw new IllegalArgumentException("Root file for file store cannot be null");

        this.root = new File(root, "papoose");

        if (LOGGER.isLoggable(Level.CONFIG)) LOGGER.config("root: " + this.root);

        init();

        LOGGER.exiting(CLASS_NAME, "FileStore");
    }

    public File getRoot()
    {
        return root;
    }

    public void clear() throws PapooseException
    {
        LOGGER.entering(CLASS_NAME, "clear");

        File properties = new File(root, PROPERTIES_FILE);
        if (properties.exists())
        {
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine(properties.toString() + " exists, will delete");

            if (!properties.delete())
            {
                PapooseException pe = new PapooseException("Unable to delete properties file");
                LOGGER.throwing(CLASS_NAME, "clear", pe);
                throw pe;
            }
        }

        File bundleRoot = FileUtils.buildPath(root, BUNDLES_DIR);
        if (bundleRoot.exists())
        {
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine(bundleRoot.toString() + " exists, will delete");

            if (!FileUtils.delete(bundleRoot))
            {
                PapooseException pe = new PapooseException("Unable to delete bundles directory");
                LOGGER.throwing(CLASS_NAME, "clear", pe);
                throw pe;
            }
        }

        File systemRoot = FileUtils.buildPath(root, SYSTEM_DIR);
        if (systemRoot.exists())
        {
            if (LOGGER.isLoggable(Level.FINE)) LOGGER.fine(systemRoot.toString() + " exists, will delete");

            if (!FileUtils.delete(systemRoot))
            {
                PapooseException pe = new PapooseException("Unable to delete system root directory");
                LOGGER.throwing(CLASS_NAME, "clear", pe);
                throw pe;
            }
        }

        init();

        LOGGER.exiting(CLASS_NAME, "clear");
    }

    public void start() throws PapooseException
    {
    }

    public void stop() throws PapooseException
    {
    }

    public synchronized List<BundleStore> loadBundleStores() throws PapooseException
    {
        LOGGER.entering(CLASS_NAME, "loadBundleStores");

        File bundlesRoot = new File(root, BUNDLES_DIR);
        List<BundleStore> result = new ArrayList<BundleStore>();

        for (String bundleId : bundlesRoot.list())
        {
            if (LOGGER.isLoggable(Level.FINEST)) LOGGER.finest("Loading bundle id " + bundleId);

            try
            {
                result.add(new BundleFileStore(new File(bundlesRoot, bundleId), Long.valueOf(bundleId)));
            }
            catch (BundleException be)
            {
                LOGGER.log(Level.WARNING, "Unable to add non-caching bundle file store for bundle id " + bundleId, be);
            }
            catch (NumberFormatException nfe)
            {
                LOGGER.log(Level.SEVERE, "Unable to add non-caching bundle file store for bundle id " + bundleId, nfe);
                throw new FatalError("Unable to add non-caching bundle file store for bundle id " + bundleId);
            }
        }

        LOGGER.exiting(CLASS_NAME, "loadBundleStores", result);

        return result;
    }

    public synchronized BundleStore obtainSystemBundleStore() throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "obtainSystemBundleStore");

        File bundleRoot = new File(root, SYSTEM_DIR);

        if (!bundleRoot.exists() && !bundleRoot.mkdirs()) throw new FatalError("Unable to create bundle store location: " + bundleRoot);

        BundleFileStore result = new BundleFileStore(bundleRoot, 0, Constants.SYSTEM_BUNDLE_LOCATION);

        LOGGER.exiting(CLASS_NAME, "obtainSystemBundleStore", result);

        return result;
    }

    public synchronized BundleStore allocateBundleStore(long bundleId, String location) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "allocateBundleStore", new Object[]{ bundleId, location });

        if (bundleId <= 0) throw new BundleException("Invalid bundle id " + bundleId);
        if (location == null) throw new BundleException("Invalid location " + location);

        File bundleRoot = FileUtils.buildPath(root, BUNDLES_DIR, bundleId);

        if (bundleRoot.exists()) throw new BundleException("Bundle store location " + bundleRoot + " already exists");
        if (!bundleRoot.mkdirs()) throw new FatalError("Unable to create bundle store location: " + bundleRoot);

        BundleFileStore result = new BundleFileStore(bundleRoot, bundleId, location);

        properties.setProperty(GENERATION_KEY + bundleId, "-1");

        save();

        LOGGER.exiting(CLASS_NAME, "allocateBundleStore", result);

        return result;
    }

    public synchronized void removeBundleStore(long bundleId) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "removeBundleStore", bundleId);

        File bundleRoot = FileUtils.buildPath(root, BUNDLES_DIR, bundleId);

        if (bundleRoot.exists())
        {
            FileUtils.delete(bundleRoot);

            properties.remove(GENERATION_KEY + bundleId);

            save();
        }
        else
        {
            LOGGER.warning("Bundle root: " + bundleRoot + " never existed");
        }

        LOGGER.exiting(CLASS_NAME, "removeBundleStore");
    }

    public synchronized ArchiveStore allocateArchiveStore(Papoose framework, long bundleId, InputStream inputStream) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "allocateArchiveStore", new Object[]{ framework, bundleId, inputStream });

        ArchiveFileStore result;
        String generationKey = GENERATION_KEY + bundleId;
        int generation;
        try
        {
            generation = Integer.parseInt(properties.getProperty(generationKey, "-1")) + 1;
        }
        catch (NumberFormatException nfe)
        {
            LOGGER.log(Level.SEVERE, "Unable to obtain last generation", nfe);
            throw new FatalError("Unable to obtain last generation", nfe);
        }

        properties.setProperty(generationKey, Integer.toString(generation));

        File archiveRoot = FileUtils.buildPath(root, BUNDLES_DIR, bundleId, GENERATIONS_DIR, generation);

        if (archiveRoot.exists()) throw new FatalError("Archive store location " + archiveRoot + " already exists");
        if (!archiveRoot.mkdirs()) throw new FatalError("Unable to create archive store location: " + archiveRoot);

        result = new ArchiveFileStore(framework, bundleId, generation, archiveRoot, inputStream);

        save();

        LOGGER.exiting(CLASS_NAME, "allocateArchiveStore", result);

        return result;
    }

    public synchronized ArchiveStore loadArchiveStore(Papoose framework, long bundleId) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "loadArchiveStore", new Object[]{ framework, bundleId });

        File archivesRoot = FileUtils.buildPath(root, BUNDLES_DIR, bundleId, GENERATIONS_DIR);

        SortedSet<Integer> generations = new TreeSet<Integer>();

        String[] list = archivesRoot.list();

        if (list == null) throw new BundleException("Bundle does not exist under id " + bundleId);

        for (String generation : list)
        {
            try
            {
                generations.add(Integer.parseInt(generation));
            }
            catch (NumberFormatException nfe)
            {
                LOGGER.log(Level.SEVERE, "Unable to parse generation id " + generation, nfe);
                throw new FatalError("Unable to parse generation id " + generation, nfe);
            }
        }

        ArchiveStore result = null;
        if (!generations.isEmpty())
        {
            int current = generations.last();

            if (current != Integer.parseInt(properties.getProperty(GENERATION_KEY + bundleId))) throw new FatalError("Generation store inconsistent with bundle store");

            generations.remove(current);

            for (Integer generation : generations)
            {
                FileUtils.delete(new File(archivesRoot, Integer.toString(generation)));
            }

            result = new ArchiveFileStore(framework, bundleId, current, new File(archivesRoot, Integer.toString(current)));
        }

        if (LOGGER.isLoggable(Level.FINER)) LOGGER.exiting(CLASS_NAME, "loadArchiveStore", result);

        return result;
    }

    private void init()
    {
        if (!this.root.exists())
        {
            if (!root.mkdirs()) throw new FatalError("Unable to create non-existant root: " + root);
        }

        File propertiesFile = new File(root, PROPERTIES_FILE);
        if (propertiesFile.exists())
        {
            load();
        }
        else
        {
            properties.put(FILESTORE_VERSION_KEY, FILESTORE_VERSION);
            save();
        }

        File bundlesRoot = new File(root, BUNDLES_DIR);
        if (!bundlesRoot.exists() && !bundlesRoot.mkdirs()) throw new FatalError("Unable to create bundles root: " + bundlesRoot);
    }

    private void load()
    {
        LOGGER.entering(CLASS_NAME, "load");

        try
        {
            properties.load(new FileInputStream(new File(this.root, PROPERTIES_FILE)));
        }
        catch (IOException ioe)
        {
            LOGGER.log(Level.SEVERE, "Unable to load bundle store state", ioe);
            throw new FatalError("Unable to save bundle store state", ioe);
        }

        LOGGER.exiting(CLASS_NAME, "load");
    }

    private void save()
    {
        LOGGER.entering(CLASS_NAME, "save");

        try
        {
            properties.store(new FileOutputStream(new File(this.root, PROPERTIES_FILE)), " bundle store state");
        }
        catch (IOException ioe)
        {
            LOGGER.log(Level.SEVERE, "Unable to save bundle store state", ioe);
            throw new FatalError("Unable to save bundle store state", ioe);
        }

        LOGGER.exiting(CLASS_NAME, "save");
    }

    @Override
    public String toString()
    {
        ToStringCreator creator = new ToStringCreator(this);

        creator.append("root", root);
        creator.append("properties", properties);

        return creator.toString();
    }
}
