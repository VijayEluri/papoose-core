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
package org.papoose.core.mock;

import java.util.Set;

import org.osgi.framework.BundleException;

import org.papoose.core.BundleGeneration;
import org.papoose.core.Generation;
import org.papoose.core.Papoose;
import org.papoose.core.PapooseException;
import org.papoose.core.descriptions.ImportDescription;
import org.papoose.core.spi.Resolver;
import org.papoose.core.spi.Solution;


/**
 *
 */
public class MockResolver implements Resolver
{
    public void start(Papoose framework) throws PapooseException { }

    public void stop() { }

    public void added(Generation bundle) { }

    public void removed(Generation bundle) { }

    public Set<Solution> resolve(Generation bundle) throws BundleException { return null; }

    public Set<Solution> resolve(BundleGeneration bundleGeneration, ImportDescription importDescription) throws BundleException { return null; }
}
