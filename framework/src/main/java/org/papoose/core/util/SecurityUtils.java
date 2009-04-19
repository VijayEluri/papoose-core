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
package org.papoose.core.util;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Logger;

import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServicePermission;


/**
 * @version $Revision$ $Date$
 */
public class SecurityUtils
{
    private final static String CLASS_NAME = SecurityUtils.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);

    public static void checkAdminPermission(Bundle bundle, String action) throws SecurityException
    {
        LOGGER.entering(CLASS_NAME, "checkAdminPermission", new Object[]{ bundle, action });

        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
        {
            LOGGER.finest("Found security manager");

            sm.checkPermission(new AdminPermission(bundle, action));
        }

        LOGGER.exiting(CLASS_NAME, "checkAdminPermission");
    }

    public static void checkServicePermission(String name, String action) throws SecurityException
    {
        LOGGER.entering(CLASS_NAME, "checkServicePermission", new Object[]{ name, action });

        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
        {
            LOGGER.finest("Found security manager");

            sm.checkPermission(new ServicePermission(name, action));
        }

        LOGGER.exiting(CLASS_NAME, "checkServicePermission");
    }

    public static void bundleChanged(final BundleListener listener, final BundleEvent event, AccessControlContext context)
    {
        if (System.getSecurityManager() == null)
        {
            listener.bundleChanged(event);
        }
        else
        {
            AccessController.doPrivileged(new PrivilegedAction<Void>()
            {
                public Void run()
                {
                    listener.bundleChanged(event);
                    return null;
                }
            }, context);
        }
    }

    public static void frameworkEvent(final FrameworkListener listener, final FrameworkEvent event, AccessControlContext context)
    {
        if (System.getSecurityManager() == null)
        {
            listener.frameworkEvent(event);
        }
        else
        {
            AccessController.doPrivileged(new PrivilegedAction<Void>()
            {
                public Void run()
                {
                    listener.frameworkEvent(event);
                    return null;
                }
            }, context);
        }
    }

    public static void serviceEvent(final ServiceListener listener, final ServiceEvent event, AccessControlContext context)
    {
        if (System.getSecurityManager() == null)
        {
            listener.serviceChanged(event);
        }
        else
        {
            AccessController.doPrivileged(new PrivilegedAction<Void>()
            {
                public Void run()
                {
                    listener.serviceChanged(event);
                    return null;
                }
            }, context);
        }
    }

    public static <T> T doPrivilegedExceptionAction(PrivilegedExceptionAction<T> action, AccessControlContext context) throws Exception
    {
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
        {
            try
            {
                return AccessController.doPrivileged(action, context);
            }
            catch (PrivilegedActionException e)
            {
                throw e.getException();
            }
        }
        else
        {
            return action.run();
        }
    }

    private SecurityUtils()
    {
    }
}
