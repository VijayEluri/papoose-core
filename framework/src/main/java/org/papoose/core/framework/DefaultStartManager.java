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
package org.papoose.core.framework;

import org.osgi.framework.Bundle;

import org.papoose.core.framework.spi.BundleManager;
import org.papoose.core.framework.spi.StartManager;

/**
 * @version $Revision$ $Date$
 */
public class DefaultStartManager implements StartManager
{
    private final BundleManager bundleManager;

    public DefaultStartManager(BundleManager bundleManager)
    {
        this.bundleManager = bundleManager;
    }

    public void start(Bundle bundle)
    {
        bundleManager.performStart(bundle);
    }
}