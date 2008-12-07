/**
 *
 * Copyright 2008 (C) The original author or authors
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
package org.papoose.core.framework.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.jcip.annotations.Immutable;

import org.papoose.core.framework.BundleImpl;
import org.papoose.core.framework.FragmentBundleImpl;
import org.papoose.core.framework.Wire;

/**
 * A bundle that can be resolved with a particular set of wires and list of
 * required bundles.
 *
 * @version $Revision$ $Date$
 */
@Immutable
public class Solution
{
    private final BundleImpl bundle;
    private final List<FragmentBundleImpl> fragments;
    private final Set<Wire> wires;
    private final List<Wire> requiredBundles;

    public Solution(BundleImpl bundle, List<FragmentBundleImpl> fragments, Set<Wire> wires, List<Wire> requiredBundles)
    {
        if (bundle == null) throw new IllegalArgumentException("Bundle cannot be null");
        if (fragments == null) throw new IllegalArgumentException("Fragments cannot be null");
        if (wires == null) throw new IllegalArgumentException("Wires cannot be null");
        if (requiredBundles == null) throw new IllegalArgumentException("Required bundles cannot be null");

        this.bundle = bundle;
        this.fragments = Collections.unmodifiableList(new ArrayList<FragmentBundleImpl>(fragments));
        this.wires = Collections.unmodifiableSet(new HashSet<Wire>(wires));
        this.requiredBundles = Collections.unmodifiableList(new ArrayList<Wire>(requiredBundles));
    }

    public BundleImpl getBundle()
    {
        return bundle;
    }

    public List<FragmentBundleImpl> getFragments()
    {
        return fragments;
    }

    public Set<Wire> getWires()
    {
        return wires;
    }

    public List<Wire> getRequiredBundles()
    {
        return requiredBundles;
    }
}
