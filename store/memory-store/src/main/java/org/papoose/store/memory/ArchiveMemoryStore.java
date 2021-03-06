/**
 *
 * Copyright 2009 (C) The original author or authors
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
package org.papoose.store.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.xbean.classloader.AbstractResourceHandle;
import org.apache.xbean.classloader.AbstractUrlResourceLocation;
import org.apache.xbean.classloader.ResourceHandle;
import org.apache.xbean.classloader.ResourceLocation;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;

import org.papoose.core.AbstractArchiveStore;
import org.papoose.core.L18nResourceBundle;
import org.papoose.core.Papoose;
import org.papoose.core.UrlUtils;
import org.papoose.core.descriptions.NativeCodeDescription;
import org.papoose.core.util.SecurityUtils;
import org.papoose.core.util.Util;

/**
 *
 */
class ArchiveMemoryStore extends AbstractArchiveStore
{
    private final static String CLASS_NAME = ArchiveMemoryStore.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);
    private final List<ResourceLocation> resourceLocations = new ArrayList<ResourceLocation>();
    private final Map<String, ResourceLocation> path2locations = new HashMap<String, ResourceLocation>();
    private final static ThreadLocal<byte[]> threadLocalArchive = new ThreadLocal<byte[]>();
    private byte[] archiveBytes;
    private final List<ZipEntry> zipEntries = new ArrayList<ZipEntry>();
    private final Map<ZipEntry, byte[]> jarContents = new HashMap<ZipEntry, byte[]>();
    private final Manifest manifest;
    private final URL codeSource;
    private transient Certificate[] certificates;

    ArchiveMemoryStore(Papoose framework, long bundleId, int generaton, InputStream inputStream) throws BundleException
    {
        super(framework, bundleId, generaton, loadAndProvideAttributes(inputStream));

        try
        {
            this.archiveBytes = threadLocalArchive.get();
            threadLocalArchive.set(null);

            assert this.archiveBytes != null;

            JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(archiveBytes));

            this.manifest = jarInputStream.getManifest();

            ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes));
            ZipEntry jarEntry;
            byte[] buffer = new byte[4096];
            while ((jarEntry = zipInputStream.getNextEntry()) != null)
            {
                zipEntries.add(jarEntry);

                if (!jarEntry.isDirectory())
                {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    Util.copy(zipInputStream, outputStream);

                    outputStream.close();

                    jarContents.put(jarEntry, outputStream.toByteArray());
                }
            }

            this.codeSource = UrlUtils.generateCodeSourceUrl(getFrameworkName(), getBundleId(), "", getGeneration());

            assert this.codeSource != null;

            for (String element : getBundleClassPath()) registerClassPathElement(element);
        }
        catch (IOException ioe)
        {
            throw new BundleException("Unable to load jar file", ioe);
        }
    }

    public void assignNativeCodeDescriptions(SortedSet<NativeCodeDescription> nativeCodeDescriptions) throws BundleException
    {
        throw new BundleException("Memory based archive store does not support native code");
    }

    public ResourceLocation registerClassPathElement(String path) throws BundleException
    {
        if (path2locations.containsKey(path)) return path2locations.get(path);

        ResourceLocation result = null;
        if (".".equals(path.trim()))
        {
            result = new BundleDirectoryResourceLocation("", resourceLocations.size());
            resourceLocations.add(result);
            path2locations.put(path, result);
        }
        else
        {
            ZipEntry entry = getZipEntry(path);
            if (entry != null)
            {
                if (entry.isDirectory())
                {
                    result = new BundleDirectoryResourceLocation(path, resourceLocations.size());
                }
                else
                {
                    result = new BundleJarResourceLocation(entry, resourceLocations.size());
                }
                resourceLocations.add(result);
                path2locations.put(path, result);
            }
        }

        return result;
    }

    public String loadLibrary(String libname)
    {
        throw new UnsupportedOperationException("Memory based archive store does not support native code");
    }

    public Enumeration<URL> findEntries(String path, String filePattern, boolean includeDirectory, boolean recurse)
    {
        if (path.startsWith("/")) path = path.substring(1);
        if (!path.endsWith("/") && path.length() > 1) path += "/";
        if (filePattern == null) filePattern = "*";

        if (path.length() == 0 && filePattern.length() == 0)
        {
            return Collections.enumeration(Collections.<URL>singleton(UrlUtils.generateEntryUrl(getFrameworkName(), getBundleId(), "", getGeneration())));
        }

        Object targets;
        try
        {
            targets = parseValue(filePattern);
            if (targets == null) return null;
        }
        catch (InvalidSyntaxException ise)
        {
            return null;
        }

        List<URL> result = new ArrayList<URL>();

        for (ZipEntry zipEntry : zipEntries)
        {
            String entryName = zipEntry.getName();
            if (entryName.startsWith(path) && (entryName.length() != path.length() || filePattern.length() == 0))
            {
                int count = 0;
                entryName = entryName.substring(path.length());
                for (int i = 0; i < entryName.length(); i++) if (entryName.charAt(i) == '/') count++;

                if (!zipEntry.isDirectory())
                {
                    if (count == 0 && Util.match(targets, entryName))
                    {
                        result.add(UrlUtils.generateEntryUrl(getFrameworkName(), getBundleId(), zipEntry.getName(), getGeneration()));
                    }
                    else if (recurse && Util.match(targets, entryName.substring(entryName.lastIndexOf('/') + 1)))
                    {
                        result.add(UrlUtils.generateEntryUrl(getFrameworkName(), getBundleId(), zipEntry.getName(), getGeneration()));
                    }
                }
                else if (includeDirectory)
                {
                    entryName = entryName.substring(0, Math.max(0, entryName.length() - 1));

                    if (count == 0 && Util.match(targets, entryName))
                    {
                        result.add(UrlUtils.generateEntryUrl(getFrameworkName(), getBundleId(), zipEntry.getName(), getGeneration()));
                    }
                    else if ((recurse || count <= 1) && Util.match(targets, entryName.substring(entryName.lastIndexOf('/') + 1)))
                    {
                        result.add(UrlUtils.generateEntryUrl(getFrameworkName(), getBundleId(), zipEntry.getName(), getGeneration()));
                    }
                }
            }
        }

        return result.isEmpty() ? null : Collections.enumeration(result);
    }

    @SuppressWarnings({ "EmptyCatchBlock" })
    public L18nResourceBundle getResourceBundle(Locale locale)
    {
        try
        {
            String path = this.getBundleLocalization();
            if (path == null) path = "OSGI-INF/l10n/bundle";
            path += (locale != null ? "_" + locale : "") + ".properties";
            ZipEntry entry = getZipEntry(path);
            if (entry != null) return new L18nResourceBundle(getInputStream(entry));
        }
        catch (IOException ioe)
        {
        }
        return null;
    }

    public InputStream getInputStreamForCodeSource() throws IOException
    {
        return new ByteArrayInputStream(archiveBytes);
    }

    public InputStream getInputStreamForEntry(String path) throws IOException
    {
        ZipEntry zipEntry = getZipEntry(path);

        if (zipEntry == null)
        {
            throw new IOException("Path does not exist: " + path);
        }
        else
        {
            return getInputStream(getZipEntry(path));
        }
    }

    public InputStream getInputStreamForResource(int location, String path) throws IOException
    {
        if (location < 0 || location >= resourceLocations.size()) throw new IOException("Resource location index is out of bounds");

        ResourceLocation resourceLocation = resourceLocations.get(location);
        ResourceHandle handle = resourceLocation.getResourceHandle(path);

        if (handle == null) throw new IOException("Path does not correspond to a resource");

        return handle.getInputStream();
    }

    public Certificate[] getCertificates()
    {
        if (certificates == null)
        {
            certificates = SecurityUtils.getCertificates(archiveBytes, getFramework().getTrustManager());
        }

        return certificates;
    }

    public void close()
    {
    }

    public String toString()
    {
        return getFrameworkName() + " " + getBundleId();
    }

    private class BundleDirectoryResourceLocation extends AbstractUrlResourceLocation
    {
        private final String path;
        private final int location;

        public BundleDirectoryResourceLocation(String path, int location)
        {
            super(codeSource);
            this.path = (path.length() == 0 || path.endsWith("/") ? path : path + "/");
            this.location = location;
        }

        public ResourceHandle getResourceHandle(String resourceName)
        {
            ArchiveMemoryStore archiveFileStore = ArchiveMemoryStore.this;

            if (resourceName.length() == 0)
            {
                return new BundleRootResourceHandle(UrlUtils.generateResourceUrl(archiveFileStore.getFrameworkName(), archiveFileStore.getBundleId(), "/", getGeneration(), location));
            }

            String entryName = path + resourceName;
            ZipEntry entry = getZipEntry(entryName);
            if (entry != null)
            {
                return new BundleDirectoryResourceHandle(entry, UrlUtils.generateResourceUrl(archiveFileStore.getFrameworkName(), archiveFileStore.getBundleId(), resourceName, getGeneration(), location));
            }
            else if (entryName.endsWith("/"))
            {
                for (ZipEntry jarEntry : zipEntries)
                {
                    if (jarEntry.getName().startsWith(entryName))
                    {
                        return new BundleDirectoryResourceHandle(entry, UrlUtils.generateResourceUrl(archiveFileStore.getFrameworkName(), archiveFileStore.getBundleId(), resourceName, getGeneration(), location));
                    }
                }
            }

            return null;
        }

        public Manifest getManifest() throws IOException
        {
            return manifest;
        }

        private class BundleDirectoryResourceHandle extends AbstractResourceHandle
        {
            private final ZipEntry entry;
            private final URL url;

            public BundleDirectoryResourceHandle(ZipEntry entry, URL url)
            {
                this.entry = entry;
                this.url = url;
            }

            public String getName() { return entry.getName(); }

            public URL getUrl() { return url; }

            public boolean isDirectory() { return entry.isDirectory(); }

            public URL getCodeSourceUrl() { return codeSource; }

            public InputStream getInputStream() throws IOException { return ArchiveMemoryStore.this.getInputStream(entry); }

            public int getContentLength() { return (int) entry.getSize(); }

            @Override
            public Manifest getManifest() throws IOException
            {
                return BundleDirectoryResourceLocation.this.getManifest();
            }

            @Override
            public Certificate[] getCertificates()
            {
                return ArchiveMemoryStore.this.getCertificates();
            }
        }

        private class BundleRootResourceHandle extends AbstractResourceHandle
        {
            private final URL url;

            public BundleRootResourceHandle(URL url)
            {
                this.url = url;
            }

            public String getName() { return "/"; }

            public URL getUrl() { return url; }

            public boolean isDirectory() { return true; }

            public URL getCodeSourceUrl() { return codeSource; }

            public InputStream getInputStream() throws IOException
            {
                return new InputStream()
                {
                    public int read() throws IOException { return -1; }
                };
            }

            public int getContentLength() { return 0; }

            @Override
            public Manifest getManifest() throws IOException
            {
                return BundleDirectoryResourceLocation.this.getManifest();
            }

            @Override
            public Certificate[] getCertificates()
            {
                return ArchiveMemoryStore.this.getCertificates();
            }
        }
    }

    private class BundleJarResourceLocation extends AbstractUrlResourceLocation
    {
        private final ZipEntry jarEntry;
        private final int location;

        public BundleJarResourceLocation(ZipEntry jarEntry, int location) throws BundleException
        {
            super(UrlUtils.generateCodeSourceUrl(getFrameworkName(), getBundleId(), jarEntry.getName(), getGeneration()));

            this.jarEntry = jarEntry;
            this.location = location;
        }

        public ResourceHandle getResourceHandle(String resourceName)
        {
            ZipInputStream zipInputStream = new ZipInputStream(ArchiveMemoryStore.this.getInputStream(jarEntry));
            ZipEntry entry;
            try
            {
                while ((entry = zipInputStream.getNextEntry()) != null)
                {
                    if (entry.getName().equals(resourceName))
                    {
                        return new BundleJarResourceHandle(entry, UrlUtils.generateResourceUrl(getFrameworkName(), getBundleId(), "/" + resourceName, getGeneration(), location));
                    }
                }
            }
            catch (IOException ioe)
            {
                LOGGER.log(Level.WARNING, "Embedded Jar " + jarEntry.getName() + " is unreadable", ioe);
            }
            return null;
        }

        public Manifest getManifest() throws IOException
        {
            JarInputStream jarInputStream = new JarInputStream(ArchiveMemoryStore.this.getInputStream(jarEntry));

            return jarInputStream.getManifest();
        }

        private class BundleJarResourceHandle extends AbstractResourceHandle
        {
            private final ZipEntry entry;
            private final URL url;

            public BundleJarResourceHandle(ZipEntry entry, URL url)
            {
                this.entry = entry;
                this.url = url;
            }

            public String getName() { return entry.getName(); }

            public URL getUrl() { return url; }

            public boolean isDirectory() { return entry.isDirectory(); }

            public URL getCodeSourceUrl() { return BundleJarResourceLocation.this.getCodeSource(); }

            public InputStream getInputStream() throws IOException
            {
                ZipInputStream zipInputStream = new ZipInputStream(ArchiveMemoryStore.this.getInputStream(jarEntry));
                ZipEntry zipEntry;
                while ((zipEntry = zipInputStream.getNextEntry()) != null)
                {
                    if (entry.getName().equals(zipEntry.getName()))
                    {
                        byte[] buffer = new byte[4096];
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        Util.copy(zipInputStream, outputStream);

                        outputStream.close();

                        return new ByteArrayInputStream(outputStream.toByteArray());
                    }
                }

                LOGGER.warning("Jar entry " + entry.getName() + " in " + jarEntry.getName() + " should have been found");

                assert false;

                return null;
            }

            public int getContentLength() { return (int) entry.getSize(); }

            @Override
            public Manifest getManifest() throws IOException
            {
                return BundleJarResourceLocation.this.getManifest();
            }

            @Override
            public Certificate[] getCertificates()
            {
                return ArchiveMemoryStore.this.getCertificates();
            }
        }
    }

    private ZipEntry getZipEntry(String path)
    {
        for (ZipEntry zipEntry : zipEntries)
        {
            String name = zipEntry.getName();

            if (name.equals(path)) return zipEntry;

            if (path.charAt(path.length() - 1) != '/')
            {
                if (name.equals(path + "/")) return zipEntry;
            }
        }
        return null;
    }

    private InputStream getInputStream(ZipEntry entry)
    {
        byte[] contents = jarContents.get(entry);
        if (contents != null)
        {
            return new ByteArrayInputStream(contents);
        }
        else
        {
            return null;
        }
    }

    private static Attributes loadAndProvideAttributes(InputStream inputStream) throws BundleException
    {
        try
        {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Util.copy(inputStream, outputStream);

            outputStream.close();

            threadLocalArchive.set(outputStream.toByteArray());

            JarInputStream jarInputStream = new JarInputStream(new ByteArrayInputStream(threadLocalArchive.get()));

            return jarInputStream.getManifest().getMainAttributes();
        }
        catch (IOException ioe)
        {
            throw new BundleException("Problems with the bundle archive", ioe);
        }
    }

}
