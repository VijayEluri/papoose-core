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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;

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
    private final Map<String, BundleImpl> locations = new HashMap<String, BundleImpl>();
    private final Map<Long, BundleImpl> bundles = new HashMap<Long, BundleImpl>();
    private long bundleCounter = 0;


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
        Set<Wire> wires = framework.getResolver().resolve(bundleImpl.getBundleImportList(), new HashSet<BundleImpl>(bundles.values()));

        bundleImpl.getClassLoader().setWires(wires);
    }

    public Bundle getBundle(long bundleId)
    {
        return null;  //todo: consider this autogenerated code
    }

    public Bundle[] getBundles()
    {
        return new Bundle[0];  //todo: consider this autogenerated code
    }

    public Bundle installBundle(String location, InputStream inputStream) throws BundleException
    {
        logger.entering(getClass().getName(), "installBundle", new Object[]{location, inputStream});

        if (locations.containsKey(location)) return locations.get(location);

        long bundleId = bundleCounter++;
        OutputStream outputStream = null;
        JarInputStream jarInputStream = null;
        try
        {
            BundleStore bundleStore = store.allocateBundleStore(bundleCounter++, 0);

            bundleStore.loadArchive(inputStream);

            jarInputStream = new JarInputStream(new FileInputStream(bundleStore.getArchive()));

            BundleImpl bundle = allocateBundle(jarInputStream.getManifest().getMainAttributes(), bundleStore, bundleId);

            bundleStore.setNativeCodeDescriptions(bundle.resolveNativeCodeDependencies());

            confirmRequiredExecutionEnvironment(bundle);

            bundle.markInstalled();

            locations.put(location, bundle);
            bundles.put(bundleId, bundle);

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
        finally
        {
            try
            {
                inputStream.close();
                if (outputStream != null) outputStream.close();
                if (jarInputStream != null) jarInputStream.close();
            }
            catch (IOException ioe)
            {
                logger.warning("Error closing stream for " + location + ". " + ioe);
            }
        }
    }

    protected BundleImpl allocateBundle(Attributes attributes, BundleStore bundleStore, long bundleId) throws Exception
    {
        String bundleActivatorClass = attributes.getValue(Constants.BUNDLE_ACTIVATOR);
        List<String> bundleCategories = obtainBundleCategories(attributes);
        List<String> bundleClasspath = obtainBundleClasspath(attributes);
        String bundleContactAddress = attributes.getValue(Constants.BUNDLE_CONTACTADDRESS);
        String bundleCopyright = attributes.getValue(Constants.BUNDLE_COPYRIGHT);
        String bundleDescription = attributes.getValue(Constants.BUNDLE_DESCRIPTION);
        String bundleDocUrl = attributes.getValue(Constants.BUNDLE_DOCURL);
        String bundleLocalization = attributes.getValue(Constants.BUNDLE_LOCALIZATION);
        short bundleManifestVersion = obtainBundleManifestVersion(attributes.getValue(Constants.BUNDLE_MANIFESTVERSION));
        String bundleName = attributes.getValue(Constants.BUNDLE_NAME);
        List<NativeCodeDescription> bundleNativeCodeList = obtainBundleNativeCodeList(attributes);
        List<String> bundleExecutionEnvironment = obtainBundleExecutionEnvironment(attributes);
        String bundleSymbolicName = attributes.getValue(Constants.BUNDLE_SYMBOLICNAME);
        URL bundleUpdateLocation = obtainBundleUpdateLocation(attributes);
        String bundleVendor = attributes.getValue(Constants.BUNDLE_VENDOR);
        Version bundleVersion = Version.parseVersion(attributes.getValue(Constants.BUNDLE_VERSION));
        List<DynamicDescription> bundleDynamicImportList = obtainBundleDynamicImportList(attributes);
        List<ExportDescription> bundleExportList = obtainBundleExportList(attributes);
        List<String> bundleExportService = obtainBundleExportService(attributes);
        FragmentDescription bundleFragmentHost = obtainBundleFragementHost(attributes);
        List<ImportDescription> bundleImportList = obtainBundleImportList(attributes);
        List<String> bundleImportService = obtainBundleImportService(attributes);
        List<RequireDescription> bundleRequireBundle = obtainBundleRequireBundle(attributes);

        boolean bundleNativeCodeListOptional = false;
        if (bundleNativeCodeList.size() > 0)
        {
            bundleNativeCodeListOptional = "*".equals(bundleNativeCodeList.get(bundleNativeCodeList.size() - 1));
        }

        return new BundleImpl(null, framework, bundleStore, bundleId,
                              bundleActivatorClass,
                              bundleCategories,
                              bundleClasspath,
                              bundleContactAddress,
                              bundleCopyright,
                              bundleDescription,
                              bundleDocUrl,
                              bundleLocalization,
                              bundleManifestVersion,
                              bundleName,
                              bundleNativeCodeList, bundleNativeCodeListOptional,
                              bundleExecutionEnvironment,
                              bundleSymbolicName,
                              bundleUpdateLocation,
                              bundleVendor,
                              bundleVersion,
                              bundleDynamicImportList,
                              bundleExportList,
                              bundleExportService,
                              bundleFragmentHost,
                              bundleImportList,
                              bundleImportService,
                              bundleRequireBundle);
    }

    protected void confirmRequiredExecutionEnvironment(BundleImpl bundle) throws BundleException
    {
        if (!bundle.getBundleExecutionEnvironment().isEmpty())
        {
            String string = (String) framework.getProperty(Constants.FRAMEWORK_EXECUTIONENVIRONMENT);
            if (string == null) throw new BundleException(Constants.FRAMEWORK_EXECUTIONENVIRONMENT + " not set");
            String[] environments = string.split(",");

            nextRequirement:
            for (String requirement : bundle.getBundleExecutionEnvironment())
            {
                for (String environment : environments)
                {
                    if (requirement.equals(environment)) continue nextRequirement;
                }
                throw new BundleException("Missing required execution environment: " + requirement);
            }
        }
    }

    protected List<String> obtainBundleCategories(Attributes attributes)
    {
        List<String> result;

        if (attributes.containsKey(Constants.BUNDLE_CATEGORY))
        {
            String[] tokens = attributes.getValue(Constants.BUNDLE_CATEGORY).split(",");
            result = new ArrayList<String>(tokens.length);

            for (String token : tokens) result.add(token.trim());
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<String> obtainBundleClasspath(Attributes attributes) throws BundleException
    {
        List<String> result;

        if (attributes.containsKey(Constants.BUNDLE_CLASSPATH))
        {
            String[] tokens = attributes.getValue(Constants.BUNDLE_CLASSPATH).split(",");
            result = new ArrayList<String>(tokens.length);

            for (String token : tokens)
            {
                token = token.trim();

                if (!Util.isValidPackageName(token)) throw new BundleException("Malformed package in Bundle-Classpath: " + token);

                result.add(token);
            }
        }
        else
        {
            result = new ArrayList<String>(1);
            result.add(".");
        }

        return result;
    }

    private short obtainBundleManifestVersion(String value)
    {
        try
        {
            return Short.parseShort(value);
        }
        catch (NumberFormatException e)
        {
            return 2;
        }
    }

    protected List<NativeCodeDescription> obtainBundleNativeCodeList(Attributes attributes) throws BundleException
    {
        List<NativeCodeDescription> result;
        if (attributes.containsKey(Constants.BUNDLE_NATIVECODE))
        {
            String[] nativecodes = Util.split(attributes.getValue(Constants.BUNDLE_NATIVECODE), ",");
            result = new ArrayList<NativeCodeDescription>(nativecodes.length);
            int ordinal = 0;

            for (String nativecode : nativecodes)
            {
                List<String> paths = new ArrayList<String>(1);
                Map<String, Object> parameters = new HashMap<String, Object>();
                NativeCodeDescription nativeCodeDescription = new NativeCodeDescription(paths, parameters, ordinal++);

                Util.parseParameters(nativecode, nativeCodeDescription, parameters, false, paths);

                if (parameters.containsKey("osversion")) parameters.put("osversion", VersionRange.parseVersionRange((String) parameters.get("osversion")));
                if (parameters.containsKey("language")) parameters.put("language", new Locale((String) parameters.get("language")));
                if (parameters.containsKey("selection-filter")) parameters.put("selection-filter", VersionRange.parseVersionRange((String) parameters.get("selection-filter")));

                result.add(nativeCodeDescription);
            }
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<String> obtainBundleExecutionEnvironment(Attributes attributes)
    {
        List<String> result;

        if (attributes.containsKey("Bundle-ExecutionEnvironment"))
        {
            String[] tokens = attributes.getValue("Bundle-ExecutionEnvironment").split(",");
            result = new ArrayList<String>(tokens.length);

            for (String token : tokens) result.add(token.trim());
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected URL obtainBundleUpdateLocation(Attributes attributes) throws Exception
    {
        if (attributes.containsKey("Bundle-UpdateLocation")) return new URL(attributes.getValue("Bundle-UpdateLocation"));
        else return null;
    }

    protected List<DynamicDescription> obtainBundleDynamicImportList(Attributes attributes) throws BundleException
    {
        List<DynamicDescription> result;

        if (attributes.containsKey("DynamicImport-Package"))
        {
            String[] importDescriptions = attributes.getValue("DynamicImport-Package").split(",");
            result = new ArrayList<DynamicDescription>(importDescriptions.length);

            for (String importDescription : importDescriptions)
            {
                List<String> paths = new ArrayList<String>(1);
                Map<String, Object> parameters = new HashMap<String, Object>();
                DynamicDescription description = new DynamicDescription(paths, parameters);

                Util.parseParameters(importDescription, description, parameters, true, paths);

                if (description.getVersion() == null) Util.callSetter(description, "version", DynamicDescription.DEFAULT_VERSION_RANGE);
                if (description.getBundleVersion() == null) Util.callSetter(description, "bundle-version", DynamicDescription.DEFAULT_VERSION_RANGE);

                result.add(description);
            }
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<ExportDescription> obtainBundleExportList(Attributes attributes) throws BundleException
    {
        List<ExportDescription> result;

        if (attributes.containsKey(Constants.EXPORT_PACKAGE))
        {
            String[] exportDescriptions = Util.split(attributes.getValue(Constants.EXPORT_PACKAGE), ",");
            result = new ArrayList<ExportDescription>(exportDescriptions.length);

            for (String exportDescription : exportDescriptions)
            {
                List<String> paths = new ArrayList<String>(1);
                Map<String, Object> parameters = new HashMap<String, Object>();
                ExportDescription description = new ExportDescription(paths, parameters);

                Util.parseParameters(exportDescription, description, parameters, true, paths);

                if (parameters.containsKey("specification-version")) parameters.put("specification-version", Version.parseVersion((String) parameters.get("specification-version")));

                if (!parameters.containsKey("version"))
                {
                    if (parameters.containsKey("specification-version")) parameters.put("version", parameters.get("specification-version"));
                    else parameters.put("version", ExportDescription.DEFAULT_VERSION);
                }
                else
                {
                    parameters.put("version", Version.parseVersion((String) parameters.get("version")));
                }

                if (parameters.containsKey("specification-version") && !parameters.get("specification-version").equals(parameters.get("version"))) throw new BundleException("version and specification-version do not match");

                if (parameters.containsKey("bundle-symbolic-name")) throw new BundleException("Attempted to set bundle-symbolic-name in Export-Package");

                if (parameters.containsKey("bundle-version")) throw new BundleException("Attempted to set bundle-version in Export-Package");

                result.add(description);
            }
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    private FragmentDescription obtainBundleFragementHost(Attributes attributes) throws BundleException
    {
        FragmentDescription fragmentDescription = null;

        if (attributes.containsKey(Constants.FRAGMENT_HOST))
        {
            Map<String, Object> parameters = new HashMap<String, Object>();
            String description = attributes.getValue(Constants.FRAGMENT_HOST);
            int index = description.indexOf(';');

            if (index != -1)
            {
                fragmentDescription = new FragmentDescription(Util.checkSymbolName(description.substring(0, index)), parameters);

                Util.parseParameters(description.substring(index + 1), fragmentDescription, parameters, true);
            }
            else
            {
                fragmentDescription = new FragmentDescription(Util.checkSymbolName(description), parameters);
            }

            if (parameters.containsKey("bundle-version")) parameters.put("bundle-version", VersionRange.parseVersionRange((String) parameters.get("bundle-verison")));
            else parameters.put("bundle-version", FragmentDescription.DEFAULT_VERSION_RANGE);
        }

        return fragmentDescription;
    }

    protected List<String> obtainBundleExportService(Attributes attributes)
    {
        List<String> result;

        if (attributes.containsKey(Constants.EXPORT_SERVICE))
        {
            String[] tokens = attributes.getValue(Constants.EXPORT_SERVICE).split(",");
            result = new ArrayList<String>(tokens.length);

            for (String token : tokens) result.add(token.trim());
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<ImportDescription> obtainBundleImportList(Attributes attributes) throws BundleException
    {
        List<ImportDescription> result;

        if (attributes.containsKey(Constants.IMPORT_PACKAGE))
        {
            Set<String> importedPaths = new HashSet<String>();
            String[] importDescriptions = attributes.getValue(Constants.IMPORT_PACKAGE).split(",");
            result = new ArrayList<ImportDescription>(importDescriptions.length);

            for (String importDescription : importDescriptions)
            {
                List<String> paths = new ArrayList<String>(1);
                Map<String, Object> parameters = new HashMap<String, Object>();

                ImportDescription description = new ImportDescription(paths, parameters);

                Util.parseParameters(importDescription, description, parameters, true, paths);

                if (parameters.containsKey("specification-version")) parameters.put("specification-version", VersionRange.parseVersionRange((String) parameters.get("specification-version")));

                if (!parameters.containsKey("version"))
                {
                    if (parameters.containsKey("specification-version")) parameters.put("version", parameters.get("specification-version"));
                    else parameters.put("version", ImportDescription.DEFAULT_VERSION_RANGE);
                }
                else
                {
                    parameters.put("version", VersionRange.parseVersionRange((String) parameters.get("version")));
                }

                if (parameters.containsKey("specification-version") && !parameters.get("specification-version").equals(parameters.get("version"))) throw new BundleException("version and specification-version do not match");

                if (parameters.containsKey("bundle-version")) parameters.put("bundle-version", VersionRange.parseVersionRange((String) parameters.get("bundle-veriosn")));
                else parameters.put("bundle-version", ImportDescription.DEFAULT_VERSION_RANGE);

                for (String path : paths)
                {
                    if (importedPaths.contains(path)) throw new BundleException("Duplicate import: " + path);
                    else importedPaths.add(path);
                }

                result.add(description);
            }
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<String> obtainBundleImportService(Attributes attributes)
    {
        List<String> result;

        if (attributes.containsKey(Constants.IMPORT_SERVICE))
        {
            String[] tokens = attributes.getValue(Constants.IMPORT_SERVICE).split(",");
            result = new ArrayList<String>(tokens.length);

            for (String token : tokens) result.add(token.trim());
        }
        else
        {
            result = Collections.emptyList();
        }

        return result;
    }

    protected List<RequireDescription> obtainBundleRequireBundle(Attributes attributes) throws BundleException
    {
        List<RequireDescription> result = null;

        if (attributes.containsKey(Constants.REQUIRE_BUNDLE))
        {
            result = new ArrayList<RequireDescription>();

            String[] descriptions = Util.split(attributes.getValue(Constants.REQUIRE_BUNDLE), ",");
            for (String description : descriptions)
            {
                Map<String, Object> parameters = new HashMap<String, Object>();
                RequireDescription requireDescription;
                int index = description.indexOf(';');

                if (index != -1)
                {
                    requireDescription = new RequireDescription(Util.checkSymbolName(description.substring(0, index)), parameters);

                    Util.parseParameters(description.substring(index + 1), requireDescription, parameters, true);
                }
                else
                {
                    requireDescription = new RequireDescription(Util.checkSymbolName(description), parameters);
                }

                if (requireDescription.getVisibility() == null) Util.callSetter(requireDescription, "visibility", Visibility.PRIVATE);

                if (requireDescription.getResolution() == null) Util.callSetter(requireDescription, "resolution", Resolution.MANDATORY);

                if (parameters.containsKey("bundle-version")) parameters.put("bundle-version", VersionRange.parseVersionRange((String) parameters.get("bundle-verison")));
                else parameters.put("bundle-version", RequireDescription.DEFAULT_VERSION_RANGE);

                result.add(requireDescription);
            }
        }

        return result;
    }
}
