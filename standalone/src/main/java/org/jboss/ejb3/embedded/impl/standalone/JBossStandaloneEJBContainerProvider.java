/*
 * JBoss, Home of Professional Open Source.
 * Copyright (c) 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.ejb3.embedded.impl.standalone;

import javax.annotation.Resource;
import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import javax.ejb.spi.EJBContainerProvider;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class JBossStandaloneEJBContainerProvider implements EJBContainerProvider
{
   private static String EJBCONTAINER_CLASS_NAME = "org.jboss.ejb3.embedded.sub.JBossSubmersibleEJBContainer";
   
   private static void addClassPath(List<URL> cp, String dirname)
   {
      try
      {
         File dir = new File(dirname);
         if(!dir.exists())
            throw new EJBException("Can't find directory " + dir.getAbsolutePath());
         File[] files = dir.listFiles();
         for(File f : files)
            cp.add(f.toURI().toURL());
      }
      catch(MalformedURLException e)
      {
         throw new EJBException(e);
      }
   }

   @Override
   public EJBContainer createEJBContainer(Map<?, ?> properties) throws EJBException
   {
      // EJB 3.1 FR 22.2.1
      // Enterprise beans running within the embeddable container are loaded using the context class loader
      // active on te thread at the time that createEJBContainer is called.
      ClassLoader beanLoader = Thread.currentThread().getContextClassLoader();

      if(properties != null)
      {
         String provider = (String) properties.get(EJBContainer.PROVIDER);
         if(provider != null)
         {
            if(!provider.equals(JBossStandaloneEJBContainerProvider.class.getName()) && !provider.equals(EJBCONTAINER_CLASS_NAME))
               return null;
         }
      }

      String jbossHome = System.getenv("JBOSS_HOME");
      if(jbossHome == null)
         jbossHome = System.getProperty("jboss.home");
      if(jbossHome == null)
         throw new EJBException("Neither JBOSS_HOME nor jboss.home is set");

      String serverConfig = System.getProperty("embedded.server.name", "default");

      List<URL> cp = new ArrayList<URL>();

      //addClassPath(cp, jbossHome + "/lib/endorsed");
      addClassPath(cp, jbossHome + "/lib");
      addClassPath(cp, jbossHome + "/common/lib");
      addClassPath(cp, jbossHome + "/server/" + serverConfig + "/lib");
      addClassPath(cp, jbossHome + "/client");

      // something wicked in JCA somewhere
      try
      {
         cp.add(new File(jbossHome + "/server/" + serverConfig + "/deployers/jboss-jca.deployer/jboss-jca-deployer.jar").toURI().toURL());
      }
      catch(MalformedURLException e)
      {
         throw new EJBException(e);
      }

      // for testing and hacking purposes you can define embedded.class.path
      String embeddedClassPath = System.getProperty("embedded.class.path");
      if(embeddedClassPath != null)
      {
         String classPathEntries[] = embeddedClassPath.split(File.pathSeparator);
         for(String s : classPathEntries)
         {
            try
            {
               cp.add(new File(s).toURI().toURL());
            }
            catch(MalformedURLException e)
            {
               throw new EJBException(e);
            }
         }
      }

      // add everything to the user supplied class loader
      URLClassLoader loader = new URLClassLoader(cp.toArray(new URL[0]), beanLoader);

      // TODO: if properties are not set do some sensible default
      //System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");

      try
      {
         // make sure lib/endorsed is properly picked up
         assert Resource.class.getMethod("lookup") != null;
         // if the class loader works out we should not have a ClassCastException down below
         assert loader.loadClass(EJBContainer.class.getName()).equals(EJBContainer.class);

         Thread.currentThread().setContextClassLoader(loader);

         Class<?> cls = loader.loadClass(EJBCONTAINER_CLASS_NAME);
         Method createMethod = cls.getMethod("createEJBContainer", Map.class, URLClassLoader.class, String.class, String.class);
         return (EJBContainer) createMethod.invoke(null, properties, loader, jbossHome, serverConfig);
      }
      catch(ClassNotFoundException e)
      {
         throw new EJBException(e);
      }
      catch (NoSuchMethodException e)
      {
         throw new EJBException(e);
      }
      catch (InvocationTargetException e)
      {
         throw new EJBException(e);
      }
      catch (IllegalAccessException e)
      {
         throw new EJBException(e);
      }
   }
}
