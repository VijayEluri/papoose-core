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
package org.papoose.core.resolver;

import java.util.Map;

import org.osgi.framework.Constants;

import org.papoose.core.ImportDescription;

/**
 * @version $Revision$ $Date$
 */
class ImportDescriptionWrapper
{
    private final String packageName;
    private final ImportDescription importDescription;
    private final boolean mandatory;

    public ImportDescriptionWrapper(String packageName, ImportDescription importDescription)
    {
        this.packageName = packageName;
        this.importDescription = importDescription;
        this.mandatory = importDescription.getParameters().containsKey(Constants.MANDATORY_DIRECTIVE) && (Boolean) importDescription.getParameters().get(Constants.MANDATORY_DIRECTIVE);
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

    public boolean isMandatory()
    {
        return mandatory;
    }
}
