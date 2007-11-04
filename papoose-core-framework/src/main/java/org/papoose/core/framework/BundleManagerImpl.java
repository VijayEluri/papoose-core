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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;

import org.papoose.core.framework.spi.ArchiveStore;
import org.papoose.core.framework.spi.BundleManager;
import org.papoose.core.framework.spi.BundleStore;
import org.papoose.core.framework.spi.Store;


/**
 * @version $Revision$ $Date$
 */
public class BundleManagerImpl implements BundleManager
{
    private final Logger logger = Logger.getLogger(getClass().getName());

    private final Papoose framework;
    private final Store store;
    private final Map<String, AbstractBundle> locations = new HashMap<String, AbstractBundle>();
    private final Map<Long, AbstractBundle> installedbundles = new HashMap<Long, AbstractBundle>();
    private final Map<Long, BundleImpl> bundles = new HashMap<Long, BundleImpl>();
    private long bundleCounter = 1;


    public BundleManagerImpl(Papoose framework, Store store)
    {
        this.framework = framework;
        this.store = store;
    }

    public InputStream getInputStream(int bundleId, int generation) throws IOException
    {
        return null;  //todo: consider this autogenerated code
    }

    public void recordBundleHasStarted(Bundle bundle)
    {
        //todo: consider this autogenerated code
    }

    public void resolve(Bundle bundle)
    {
        BundleImpl bundleImpl = (BundleImpl) bundle;
        ArchiveStore currentStore = bundleImpl.getCurrentStore();
        Set<Wire> wires = framework.getResolver().resolve(currentStore.getBundleImportList(), new HashSet<BundleImpl>(bundles.values()));
        List<Wire> requiredBundles = new ArrayList<Wire>();

        String bootDelegateString = (String) framework.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
        String[] bootDelegates = (bootDelegateString == null ? new String[]{ } : bootDelegateString.split(","));

        Set<String> exportedPackages = new HashSet<String>();

        for (ImportDescription desc : currentStore.getBundleImportList())
        {
            exportedPackages.addAll(desc.getPackageNames());
        }

        for (ExportDescription desc : currentStore.getBundleExportList())
        {
            exportedPackages.addAll(desc.getPackages());
        }

        for (Wire wire : requiredBundles)
        {
            exportedPackages.add(wire.getPackageName());
        }

        BundleClassLoader classLoader = new BundleClassLoader(bundle.getLocation(),
                                                              framework.getClassLoader(),
                                                              framework,
                                                              bundleImpl,
                                                              requiredBundles,
                                                              bootDelegates,
                                                              exportedPackages.toArray(new String[exportedPackages.size()]),
                                                              currentStore.getDynamicImportSet(),
                                                              bundleImpl.getStores());

        bundleImpl.setClassLoader(classLoader);
    }

    public Bundle getBundle(long bundleId)
    {
        return installedbundles.get(bundleId);
    }

    public Bundle getBundle(String symbolicName)
    {
        return null;  //todo: consider this autogenerated code
    }

    public Bundle[] getBundles()
    {
        return new Bundle[0];  //todo: consider this autogenerated code
    }

    public Bundle installBundle(String location, InputStream inputStream) throws BundleException
    {
        logger.entering(getClass().getName(), "installBundle", new Object[]{ location, inputStream });

        if (locations.containsKey(location)) return locations.get(location);

        long bundleId = bundleCounter++;
        try
        {
            BundleStore bundleStore = store.allocateBundleStore(bundleId, location);

            AbstractStore archiveStore = store.allocateArchiveStore(framework, bundleId, 0, inputStream);

            AbstractBundle bundle = allocateBundle(bundleId, location, bundleStore, archiveStore);

            bundle.markInstalled();

            locations.put(location, bundle);
            installedbundles.put(bundleId, bundle);
            if (bundle instanceof BundleImpl) bundles.put(bundleId, (BundleImpl) bundle);

            return bundle;
        }
        catch (BundleException be)
        {
            store.removeBundleStore(bundleId);
            throw be;
        }
        catch (Exception e)
        {
            store.removeBundleStore(bundleId);
            throw new BundleException("Error occured while loading location " + location, e);
        }
    }

    private AbstractBundle allocateBundle(long bundleId, String location, BundleStore bundleStore, AbstractStore archiveStore)
    {
        return new BundleImpl(framework, bundleId, location, bundleStore, archiveStore);
    }
}
