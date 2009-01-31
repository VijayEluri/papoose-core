/**
 *
 * Copyright 2007-2009 (C) The original author or authors
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
package org.papoose.core;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.apache.xbean.classloader.ResourceLocation;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServicePermission;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;

import org.papoose.core.spi.ArchiveStore;
import org.papoose.core.spi.BootClasspathManager;
import org.papoose.core.spi.BundleStore;
import org.papoose.core.spi.Solution;
import org.papoose.core.spi.StartManager;
import org.papoose.core.spi.Store;
import org.papoose.core.util.ToStringCreator;


/**
 * @version $Revision$ $Date$
 */
public class BundleManager
{
    private final static String CLASS_NAME = BundleManager.class.getName();
    private final Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Map<String, BundleController> locations = new HashMap<String, BundleController>();
    private final Map<NameVersionKey, BundleController> nameVersions = new HashMap<NameVersionKey, BundleController>();
    private final Map<Long, BundleController> installedbundles = new HashMap<Long, BundleController>();
    private final Map<Long, BundleController> bundles = new HashMap<Long, BundleController>();
    private final Papoose framework;
    private final Store store;
    private StartManager startManager;
    private long bundleCounter = 0;


    public BundleManager(Papoose framework, Store store)
    {
        this.framework = framework;
        this.store = store;
        this.startManager = new DefaultStartManager(this);
    }

    public Store getStore()
    {
        return store;
    }

    public void setStartManager(StartManager startManager)
    {
        this.startManager = startManager;
    }

    public InputStream getInputStreamForCodesource(long bundleId, int generationId) throws IOException
    {
        if (!bundles.containsKey(bundleId)) throw new IOException("Unable to find bundle " + bundleId);

        BundleController bundle = bundles.get(bundleId);
        Generation generation = (bundle != null ? bundle.getGenerations().get(generationId) : null);
        if (generation != null) return generation.getArchiveStore().getInputStreamForCodeSource();

        throw new IOException("Unable to find archive store generation " + generationId + " for bundle " + bundleId);
    }

    public InputStream getInputStreamForEntry(long bundleId, int generationId, String path) throws IOException
    {
        if (!bundles.containsKey(bundleId)) throw new IOException("Unable to find bundle " + bundleId);

        BundleController bundle = bundles.get(bundleId);
        Generation generation = (bundle != null ? bundle.getGenerations().get(generationId) : null);
        if (generation != null) return generation.getArchiveStore().getInputStreamForEntry(path);

        throw new IOException("Unable to find archive store generation " + generationId + " for bundle " + bundleId);
    }

    public InputStream getInputStreamForResource(long bundleId, int generationId, int location, String path) throws IOException
    {
        if (!bundles.containsKey(bundleId)) throw new IOException("Unable to find bundle " + bundleId);

        BundleController bundle = bundles.get(bundleId);
        Generation generation = (bundle != null ? bundle.getGenerations().get(generationId) : null);
        if (generation != null) return generation.getArchiveStore().getInputStreamForResource(location, path);

        throw new IOException("Unable to find archive store generation " + generationId + " for bundle " + bundleId);
    }

    public void recordBundleHasStarted(Bundle bundle)
    {
        //todo: consider this autogenerated code
    }

    public Bundle getBundle(long bundleId)
    {
        return installedbundles.get(bundleId);
    }

    public Bundle[] getBundles()
    {
        return new Bundle[0];  //todo: consider this autogenerated code
    }

    public Bundle installSystemBundle(Version version) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "installSystemBundle");

        if (locations.containsKey(Constants.SYSTEM_BUNDLE_LOCATION)) throw new BundleException("System bundle already installed");

        final long systemBundleId = 0;
        try
        {
            BundleStore bundleStore = store.allocateBundleStore(systemBundleId, Constants.SYSTEM_BUNDLE_LOCATION);

            BundleController systemBundle = new SystemBundleController(framework, bundleStore, version);

            NameVersionKey key = new NameVersionKey(systemBundle.getSymbolicName(), version);

            if (nameVersions.containsKey(key)) throw new BundleException("Bundle already registered with name " + key.getSymbolicName() + " and version " + key.getVersion());

            nameVersions.put(key, systemBundle);
            locations.put(Constants.SYSTEM_BUNDLE_LOCATION, systemBundle);
            installedbundles.put(systemBundleId, systemBundle);
            bundles.put(systemBundleId, systemBundle);

            framework.getResolver().added(systemBundle.getCurrentGeneration());

            bundleStore.markModified();

            LOGGER.exiting(CLASS_NAME, "installSystemBundle", systemBundle);

            return systemBundle;
        }
        catch (BundleException be)
        {
            store.removeBundleStore(systemBundleId);
            throw be;
        }
        catch (Exception e)
        {
            store.removeBundleStore(systemBundleId);
            throw new BundleException("Error occured while loading location " + Constants.SYSTEM_BUNDLE_LOCATION, e);
        }
    }

    public void uninstallSystemBundle()
    {

    }

    public Bundle installBundle(String location, InputStream inputStream) throws BundleException
    {
        LOGGER.entering(CLASS_NAME, "installBundle", new Object[]{ location, inputStream });

        if (locations.containsKey(location)) return locations.get(location);

        long bundleId = ++bundleCounter;
        try
        {
            BundleStore bundleStore = store.allocateBundleStore(bundleId, location);

            ArchiveStore archiveStore = store.allocateArchiveStore(framework, bundleId, inputStream);

            archiveStore.assignNativeCodeDescriptions(resolveNativeCodeDependencies(archiveStore.getBundleNativeCodeList()));

            confirmRequiredExecutionEnvironment(archiveStore.getBundleRequiredExecutionEnvironment());

            BundleController bundle = new BundleController(framework, bundleStore);

            Generation generation = allocateGeneration(bundle, archiveStore);

            bundle.getGenerations().put(archiveStore.getGeneration(), generation);
            bundle.setCurrentGeneration(generation);

            NameVersionKey key = new NameVersionKey(archiveStore.getBundleSymbolicName(), archiveStore.getBundleVersion());

            if (nameVersions.containsKey(key)) throw new BundleException("Bundle already registered with name " + key.getSymbolicName() + " and version " + key.getVersion());

            nameVersions.put(key, bundle);
            locations.put(location, bundle);
            installedbundles.put(bundleId, bundle);
            bundles.put(bundleId, bundle);

            framework.getResolver().added(generation);

            bundleStore.markModified();

            generation.setState(Bundle.INSTALLED);

            fireBundleEvent(new BundleEvent(BundleEvent.INSTALLED, bundle));

            LOGGER.exiting(CLASS_NAME, "installBundle", bundle);

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

    /**
     * Make sure that at least one native code description is valid.
     *
     * @return a list of resolvable native code descriptions
     * @throws BundleException if the method is unable to find at least one valid native code description
     */
    private SortedSet<NativeCodeDescription> resolveNativeCodeDependencies(List<NativeCodeDescription> bundleNativeCodeList) throws BundleException
    {
        SortedSet<NativeCodeDescription> set = new TreeSet<NativeCodeDescription>();

        if (!bundleNativeCodeList.isEmpty())
        {
            VersionRange osVersionRange = VersionRange.parseVersionRange((String) framework.getProperty(Constants.FRAMEWORK_OS_VERSION));

            nextDescription:
            for (NativeCodeDescription description : bundleNativeCodeList)
            {
                Map<String, Object> parameters = description.getParameters();
                for (String key : parameters.keySet())
                {
                    if ("osname".equals(key) && !framework.getProperty(Constants.FRAMEWORK_OS_NAME).equals(parameters.get(key)))
                    {
                        continue nextDescription;
                    }
                    else if ("processor".equals(key) && !framework.getProperty(Constants.FRAMEWORK_PROCESSOR).equals(parameters.get(key)))
                    {
                        continue nextDescription;
                    }
                    else if ("osversion".equals(key))
                    {
                        if (!osVersionRange.includes(description.getOsVersion())) continue nextDescription;
                    }
                    else if ("language".equals(key) && !framework.getProperty(Constants.FRAMEWORK_LANGUAGE).equals(description.getLanguage()))
                    {
                        continue nextDescription;
                    }
                    else if ("selection-filter".equals(key))
                    {
                        try
                        {
                            Filter selectionFilter = new FilterImpl(framework.getParser().parse((String) parameters.get(key)));
                            if (!selectionFilter.match(framework.getProperties())) continue nextDescription;
                        }
                        catch (InvalidSyntaxException ise)
                        {
                            throw new BundleException("Invalid selection filter", ise);
                        }
                    }
                }

                set.add(description);
            }
        }

        return set;
    }

    protected void confirmRequiredExecutionEnvironment(List<String> bundleExecutionEnvironment) throws BundleException
    {
        if (!bundleExecutionEnvironment.isEmpty())
        {
            String string = (String) framework.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
            if (string == null) throw new BundleException(Constants.FRAMEWORK_EXECUTIONENVIRONMENT + " not set");
            String[] environments = string.split(",");

            nextRequirement:
            for (String requirement : bundleExecutionEnvironment)
            {
                for (String environment : environments)
                {
                    if (requirement.equals(environment)) continue nextRequirement;
                }
                throw new BundleException("Missing required execution environment: " + requirement);
            }
        }
    }

    public boolean resolve(Bundle target)
    {
        if (target.getState() != Bundle.INSTALLED) return false;

        try
        {
            BundleController bundleController = (BundleController) target;
            Set<Solution> solutions = framework.getResolver().resolve(bundleController.getCurrentGeneration());

            String bootDelegateString = (String) framework.getProperty(Constants.FRAMEWORK_BOOTDELEGATION);
            String[] bootDelegates = (bootDelegateString == null ? new String[]{ } : bootDelegateString.split(","));

            for (int i = 0; i < bootDelegates.length; i++) bootDelegates[i] = bootDelegates[i].trim();

            for (Solution solution : solutions)
            {
                BundleGeneration bundle = solution.getBundle();
                ArchiveStore currentStore = bundle.getArchiveStore();
                Set<Wire> wires = solution.getWires();
                List<Wire> requiredBundles = new ArrayList<Wire>();

                Set<String> exportedPackages = new HashSet<String>();

                for (ImportDescription desc : currentStore.getBundleImportList())
                {
                    exportedPackages.addAll(desc.getPackages());
                }

                for (ExportDescription desc : currentStore.getBundleExportList())
                {
                    exportedPackages.addAll(desc.getPackages());
                }

                for (Wire wire : requiredBundles)
                {
                    exportedPackages.add(wire.getPackageName());
                }

                List<ResourceLocation> resourceLocations = new ArrayList<ResourceLocation>();
                Set<ArchiveStore> archiveStores = new HashSet<ArchiveStore>();

                BundleClassLoader classLoader = new BundleClassLoader(bundle.getBundleController().getLocation(),
                                                                      framework.getClassLoader(),
                                                                      framework,
                                                                      bundle,
                                                                      wires,
                                                                      requiredBundles,
                                                                      bootDelegates,
                                                                      exportedPackages.toArray(new String[exportedPackages.size()]),
                                                                      currentStore.getDynamicImportSet(),
                                                                      resourceLocations,
                                                                      archiveStores);

                bundle.setClassLoader(classLoader);

                //todo:        bundle.setResolvedState();
            }
        }
        catch (BundleException e)
        {
            e.printStackTrace();  //todo: consider this autogenerated code
            return false;
        }
        return true; // todo: not correct
    }

    public Wire resolve(DynamicDescription dynamicDescription)
    {
        return null; // todo:
    }

    public void loadAndStartBundles()
    {
        List<BundleStore> bundleStores = null;
        try
        {
            bundleStores = store.loadBundleStores();
        }
        catch (PapooseException e)
        {
            throw new FatalError("Unable to load bundle stores", e);
        }

        for (BundleStore bundleStore : bundleStores)
        {
            try
            {
                long bundleId = bundleStore.getBundleId();

                if (bundleId == 0) continue;

                String location = bundleStore.getLocation();
                ArchiveStore archiveStore = store.loadArchiveStore(framework, bundleId);

                archiveStore.assignNativeCodeDescriptions(resolveNativeCodeDependencies(archiveStore.getBundleNativeCodeList()));

                confirmRequiredExecutionEnvironment(archiveStore.getBundleRequiredExecutionEnvironment());

                BundleController bundle = new BundleController(framework, bundleStore);

                Generation generation = allocateGeneration(bundle, archiveStore);

                bundle.getGenerations().put(archiveStore.getGeneration(), generation);
                bundle.setCurrentGeneration(generation);

                NameVersionKey key = new NameVersionKey(archiveStore.getBundleSymbolicName(), archiveStore.getBundleVersion());

                if (nameVersions.containsKey(key)) throw new BundleException("Bundle already registered with name " + key.getSymbolicName() + " and version " + key.getVersion());

                nameVersions.put(key, bundle);
                locations.put(location, bundle);
                installedbundles.put(bundleId, bundle);
                bundles.put(bundleId, bundle);

                bundleCounter = Math.max(bundleCounter, bundleId);

                if (bundle.getCurrentGeneration() instanceof BundleGeneration)
                {
                    framework.getResolver().added(bundle.getCurrentGeneration());
                    if (archiveStore.getBundleActivatorClass() != null) startManager.start((BundleGeneration) generation);
                }
            }
            catch (BundleException e)
            {
                e.printStackTrace();  //todo: consider this autogenerated code
            }
        }
    }

    public void requestStart(BundleGeneration bundle)
    {
        startManager.start(bundle);
    }

    public void requestStop(BundleGeneration bundle)
    {
        startManager.stop(bundle);
    }

    public void performStart(BundleGeneration bundle)
    {
        //todo: consider this autogenerated code
    }

    public void performStop(BundleGeneration bundle)
    {
        //todo: consider this autogenerated code
    }

    public void uninstall(Bundle bundle)
    {
        //todo: consider this autogenerated code
        BundleController bundleController = (BundleController) bundle;
        for (Generation generation : bundleController.getGenerations().values())
        {
            framework.getResolver().removed(generation);
        }
    }

    public void unregisterServices(Bundle bundle)
    {
        //todo: consider this autogenerated code
    }

    public void releaseServices(Bundle bundle)
    {
        //todo: consider this autogenerated code
    }

    public void fireBundleEvent(final BundleEvent event)
    {
        Collection<BundleController> bundles = installedbundles.values();

        for (final BundleController bundle : bundles)
        {
            for (final BundleListener listener : bundle.getSyncBundleListeners())
            {
                try
                {
                    if (System.getSecurityManager() == null)
                    {
                        if (bundle.getState() == Bundle.ACTIVE) listener.bundleChanged(event);
                    }
                    else
                    {
                        AccessController.doPrivileged(new PrivilegedAction<Void>()
                        {
                            public Void run()
                            {
                                if (bundle.getState() == Bundle.ACTIVE) listener.bundleChanged(event);
                                return null;
                            }
                        });
                    }
                }
                catch (Throwable throwable)
                {
                    fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, bundle, throwable));
                }
            }
        }

        if ((event.getType() & (BundleEvent.LAZY_ACTIVATION | BundleEvent.STARTING | BundleEvent.STOPPING)) == 0)
        {
            for (final BundleController bundle : bundles)
            {
                for (final BundleListener listener : bundle.getBundleListeners())
                {
                    bundle.getSerialExecutor().execute(new Runnable()
                    {
                        public void run()
                        {
                            try
                            {
                                if (System.getSecurityManager() == null)
                                {
                                    if (bundle.getState() == Bundle.ACTIVE) listener.bundleChanged(event);
                                }
                                else
                                {
                                    AccessController.doPrivileged(new PrivilegedAction<Void>()
                                    {
                                        public Void run()
                                        {
                                            if (bundle.getState() == Bundle.ACTIVE) listener.bundleChanged(event);
                                            return null;
                                        }
                                    });
                                }
                            }
                            catch (Throwable throwable)
                            {
                                fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, bundle, throwable));
                            }
                        }
                    });
                }

            }
        }
    }

    public void fireFrameworkEvent(final FrameworkEvent event)
    {
        for (final BundleController bundle : installedbundles.values())
        {
            for (final FrameworkListener listener : bundle.getFrameworkListeners())
            {
                bundle.getSerialExecutor().execute(new Runnable()
                {
                    public void run()
                    {
                        try
                        {
                            if (System.getSecurityManager() == null)
                            {
                                if (bundle.getState() == Bundle.ACTIVE) listener.frameworkEvent(event);
                            }
                            else
                            {
                                AccessController.doPrivileged(new PrivilegedAction<Void>()
                                {
                                    public Void run()
                                    {
                                        if (bundle.getState() == Bundle.ACTIVE) listener.frameworkEvent(event);
                                        return null;
                                    }
                                });
                            }
                        }
                        catch (Throwable throwable)
                        {
                            if (event.getType() != FrameworkEvent.ERROR)
                            {
                                fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, bundle, throwable));
                            }
                        }
                    }
                });
            }
        }
    }

    public void fireServiceEvent(final ServiceEvent event)
    {
        ServiceReference reference = event.getServiceReference();
        String[] classes = (String[]) reference.getProperty(Constants.OBJECTCLASS);

        for (BundleController bundle : installedbundles.values())
        {
            for (String clazz : classes)
            {
                if (!bundle.hasPermission(new ServicePermission(clazz, ServicePermission.GET))) continue;

                fireServiceEvent(event, bundle.getAllServiceListeners(), bundle);

                if (!reference.isAssignableTo(bundle, clazz)) continue;

                fireServiceEvent(event, bundle.getServiceListeners(), bundle);
            }
        }
    }

    public void readLock() throws InterruptedException
    {
        readWriteLock.readLock().lockInterruptibly();
    }

    public void readUnlock()
    {
        readWriteLock.readLock().unlock();
    }

    public void writeLock() throws InterruptedException
    {
        readWriteLock.writeLock().lockInterruptibly();
    }

    public void writeUnlock()
    {
        readWriteLock.writeLock().unlock();
    }

    protected void fireServiceEvent(final ServiceEvent event, Set<ServiceListener> listeners, final Bundle bundle)
    {
        for (final ServiceListener listener : listeners)
        {
            try
            {
                if (System.getSecurityManager() == null)
                {
                    if (bundle.getState() == Bundle.ACTIVE) listener.serviceChanged(event);
                }
                else
                {
                    AccessController.doPrivileged(new PrivilegedAction<Void>()
                    {
                        public Void run()
                        {
                            if (bundle.getState() == Bundle.ACTIVE) listener.serviceChanged(event);
                            return null;
                        }
                    });
                }
            }
            catch (Throwable throwable)
            {
                fireFrameworkEvent(new FrameworkEvent(FrameworkEvent.ERROR, bundle, throwable));
            }
        }
    }

    private Generation allocateGeneration(BundleController bundle, ArchiveStore archiveStore) throws BundleException
    {
        if (archiveStore.getBundleFragmentHost() != null)
        {
            FragmentDescription description = archiveStore.getBundleFragmentHost();
            if (description.getExtension() == null)
            {
                return new FragmentGeneration(bundle, archiveStore);
            }
            else
            {
                if (!archiveStore.getBundleImportList().isEmpty()) throw new BundleException("Extension bundles cannot import packages");
                if (!archiveStore.getBundleRequireBundle().isEmpty()) throw new BundleException("Extension bundles cannot require other bundles");
                if (!archiveStore.getBundleNativeCodeList().isEmpty()) throw new BundleException("Extension bundles cannot load native code");
                if (!archiveStore.getDynamicImportSet().isEmpty()) throw new BundleException("Extension bundles cannot dynamically import packages");
                if (archiveStore.getBundleActivatorClass() != null) throw new BundleException("Extension bundles cannot have a bundle activator");

                if (archiveStore.getBundleFragmentHost().getExtension() == Extension.FRAMEWORK)
                {
                    return new FrameworkExtensionGeneration(bundle, archiveStore);
                }
                else
                {
                    BootClasspathManager manager = (BootClasspathManager) framework.getProperty(BootClasspathManager.BOOT_CLASSPATH_MANAGER);
                    if (manager == null || !manager.isSupported()) throw new BundleException("Boot classpath extensions not supported in this framework configuration");

                    return new BootClassExtensionGeneration(bundle, archiveStore);
                }
            }
        }
        else
        {
            return new BundleGeneration(bundle, archiveStore);
        }
    }

    public boolean resolve(Generation result)
    {
        return false;  //To change body of created methods use File | Settings | File Templates.
    }

    /**
     * Simple class used as a key to make sure that symbolic name/key
     * combinations are unique
     */
    private static class NameVersionKey
    {
        private final String symbolicName;
        private final Version version;

        private NameVersionKey(String symbolicName, Version version)
        {
            assert symbolicName != null && symbolicName.trim().length() != 0;
            assert version != null;

            this.symbolicName = symbolicName;
            this.version = version;
        }

        public String getSymbolicName()
        {
            return symbolicName;
        }

        public Version getVersion()
        {
            return version;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            NameVersionKey that = (NameVersionKey) o;

            //noinspection SimplifiableIfStatement
            if (!symbolicName.equals(that.symbolicName)) return false;
            return version.equals(that.version);

        }

        @Override
        public int hashCode()
        {
            int result = symbolicName.hashCode();
            result = 31 * result + version.hashCode();
            return result;
        }

        @Override
        public String toString()
        {
            ToStringCreator creator = new ToStringCreator(this);

            creator.append("symbolicName", symbolicName);
            creator.append("version", version);

            return creator.toString();
        }
    }
}