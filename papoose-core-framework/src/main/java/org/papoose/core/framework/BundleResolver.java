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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.osgi.framework.Constants;
import org.osgi.framework.Version;


/**
 * @version $Revision$ $Date$
 */
public class BundleResolver
{
    /**
     * Collect set of wires which are a consistent pairing of imports to exports.
     *
     * @param bundleImportList list of imports to be matched
     * @param bundles          List of bundles that can provide the exports
     * @return list of consistent set of wires
     */
    Set<Wire> resolve(List<ImportDescription> bundleImportList, Set<BundleImpl> bundles)
    {
        return resolve(collectPackages(bundleImportList), bundles, new HashSet<Candidate>(), new HashSet<Candidate>());
    }

    private static Set<Wire> resolve(List<ImportDescriptionWrapper> imports, Set<BundleImpl> bundles, Set<Candidate> candidates, Set<Candidate> impliedSet)
    {
        imports = new ArrayList<ImportDescriptionWrapper>(imports);

        ImportDescriptionWrapper targetImport = imports.remove(0);

        for (ExportDescriptionWrapper candidate : collectEligibleExports(targetImport, bundles))
        {
            if (match(targetImport.getPackageName(), targetImport.getImportDescription(), candidate.getExportDescription()))
            {
                Set<Candidate> impliedCandidates = collectImpliedConstraints(candidate.getExportDescription().getUses(), candidate.getBundle());

                assert impliedCandidates != null;

                if (isConsistent(impliedSet, impliedCandidates))
                {
                    Set<Candidate> candidatesSavePoint = new HashSet<Candidate>(candidates);
                    candidatesSavePoint.add(new Candidate(targetImport.getPackageName(), candidate.getExportDescription(), candidate.getBundle()));

                    if (imports.isEmpty())
                    {
                        return collectWires(candidatesSavePoint);
                    }
                    else
                    {
                        Set<Candidate> impliedSetSavePoint = new HashSet<Candidate>(impliedSet);
                        impliedSetSavePoint.addAll(impliedCandidates);

                        Set<Wire> result = resolve(imports, bundles, candidatesSavePoint, impliedSetSavePoint);
                        if (!result.isEmpty()) return result;
                    }
                }
            }
        }

        return Collections.emptySet();
    }

    private static SortedSet<ExportDescriptionWrapper> collectEligibleExports(ImportDescriptionWrapper importWrapper, Set<BundleImpl> bundles)
    {
        String bundleName = (String) importWrapper.getParameters().get(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE);
        VersionRange bundleVersionRange = (VersionRange) importWrapper.getParameters().get(Constants.BUNDLE_VERSION_ATTRIBUTE);
        boolean baseNameMatch = bundleName == null || bundleName.equals(Constants.SYSTEM_BUNDLE_SYMBOLICNAME);
        boolean baseVersionMatch = bundleVersionRange == null;

        SortedSet<ExportDescriptionWrapper> sorted = new TreeSet<ExportDescriptionWrapper>();

        for (BundleImpl bundle : bundles)
        {
            boolean nameMatch = baseNameMatch || bundleName.equals(bundle.getCurrentStore().getBundleSymbolicName());
            boolean versionMatch = baseVersionMatch || bundleVersionRange.includes(bundle.getCurrentStore().getBundleVersion());

            if (nameMatch && versionMatch)
            {
                for (ExportDescription exportDescription : bundle.getCurrentStore().getBundleExportList())
                {
                    sorted.add(new ExportDescriptionWrapper(exportDescription, bundle));
                }
            }
        }

        return sorted;
    }

    protected static Set<Candidate> collectImpliedConstraints(Set<String> uses, BundleImpl bundle)
    {
        Set<Candidate> result = new HashSet<Candidate>();

        nextPackage:
        for (String packageName : uses)
        {
            for (Wire wire : bundle.getClassLoader().getWires())
            {
                if (packageName.equals(wire.getPackageName()))
                {
                    ExportDescription exportDescription = wire.getExportDescription();

                    result.addAll(collectImpliedConstraints(exportDescription.getUses(), wire.getBundle()));
                    result.add(new Candidate(packageName, exportDescription, wire.getBundle()));

                    continue nextPackage;
                }
            }
        }
        return result;
    }

    private static boolean isConsistent(Set<Candidate> currentCandiates, Set<Candidate> testCandiates)
    {
        Set<Candidate> intersection = new HashSet<Candidate>(currentCandiates);

        intersection.retainAll(testCandiates);

        for (Candidate candidate : intersection)
        {
            ExportDescription version = candidate.getExportDescription();
            for (Candidate c : testCandiates)
            {
                if (!version.equals(c.getExportDescription())) return false;
            }

        }
        return true;
    }

    private static Set<Wire> collectWires(Set<Candidate> candidates)
    {
        Set<Wire> wires = new HashSet<Wire>();

        for (Candidate candidate : candidates)
        {
            wires.add(new Wire(candidate.getPackageName(), candidate.getExportDescription(), candidate.getBundle()));
        }

        return wires;
    }

    private static boolean match(String importPackage, ImportDescription importDescription, ExportDescription exportDescription)
    {
        for (String exportPackage : exportDescription.getPackages())
        {
            if (importPackage.equals(exportPackage))
            {
                VersionRange importVersionRange = (VersionRange) importDescription.getParameters().get("version");
                if (importVersionRange.includes((Version) exportDescription.getParameters().get("version")))
                {
                    for (String key : exportDescription.getMandatory())
                    {
                        if (!exportDescription.getParameters().get(key).equals(importDescription.getParameters().get(key))) return false;
                    }

                    for (String key : importDescription.getParameters().keySet())
                    {
                        if ("version".equals(key) || "bundle-version".equals(key)) continue;
                        if (!importDescription.getParameters().get(key).equals(exportDescription.getParameters().get(key))) return false;
                    }

                    return true;
                }
                break;
            }
        }

        return false;
    }

    /**
     * Import descriptions can contain many packages.  We need the individual packages.
     *
     * @param importDescriptions a list of import descriptions
     * @return the list of packages contained in the list of import descriptions
     */
    private static List<ImportDescriptionWrapper> collectPackages(List<ImportDescription> importDescriptions)
    {
        List<ImportDescriptionWrapper> work = new ArrayList<ImportDescriptionWrapper>();

        for (ImportDescription importDescription : importDescriptions)
        {
            for (String packageName : importDescription.getPackageNames())
            {
                work.add(new ImportDescriptionWrapper(packageName, importDescription));
            }
        }

        return work;
    }

    private static class ImportDescriptionWrapper
    {
        private final String packageName;
        private final ImportDescription importDescription;

        public ImportDescriptionWrapper(String packageName, ImportDescription importDescription)
        {
            this.packageName = packageName;
            this.importDescription = importDescription;
        }

        public String getPackageName()
        {
            return packageName;
        }

        public Map<String, Object> getParameters()
        {
            return importDescription.getParameters();
        }

        public ImportDescription getImportDescription()
        {
            return importDescription;
        }

        public String toString()
        {
            return packageName;
        }
    }

    protected static class Candidate
    {
        private final String packageName;
        private final ExportDescription exportDescription;
        private final BundleImpl bundle;

        public Candidate(String packageName, ExportDescription exportDescription, BundleImpl bundle)
        {
            assert packageName != null;
            assert exportDescription != null;
            assert bundle != null;

            this.packageName = packageName;
            this.exportDescription = exportDescription;
            this.bundle = bundle;
        }

        public String getPackageName()
        {
            return packageName;
        }

        public ExportDescription getExportDescription()
        {
            return exportDescription;
        }

        public BundleImpl getBundle()
        {
            return bundle;
        }

        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Candidate candidate = (Candidate) o;

            return packageName.equals(candidate.packageName);
        }

        public int hashCode()
        {
            return packageName.hashCode();
        }
    }

    /**
     * A simple wrapper to make sure that export descriptions are searched in
     * the proper order.  This wrapper assumes that <code>BundleImpl</code>
     * classes initially sort by their resolution status, i.e. resolved bundles
     * appear before un-resolved bundles.
     */
    private static class ExportDescriptionWrapper implements Comparable<ExportDescriptionWrapper>
    {
        private final ExportDescription exportDescription;
        private final BundleImpl bundle;
        private final long bundleId;
        private final Version version;

        public ExportDescriptionWrapper(ExportDescription exportDescription, BundleImpl bundle)
        {
            this.exportDescription = exportDescription;
            this.bundle = bundle;
            this.bundleId = bundle.getBundleId();
            this.version = (Version) exportDescription.getParameters().get("version");
        }

        public ExportDescription getExportDescription()
        {
            return exportDescription;
        }

        public BundleImpl getBundle()
        {
            return bundle;
        }

        public int compareTo(ExportDescriptionWrapper o)
        {
            int result = version.compareTo(o.version);
            if (result == 0) result = (int) (bundleId - o.bundleId);
            return result;
        }
    }
}
