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
package org.jboss.ejb3.embedded.sub;

import org.jboss.bootstrap.api.as.config.JBossASServerConfig;
import org.jboss.ejb3.embedded.impl.base.scanner.ClassPathEjbJarScanner;
import org.jboss.ejb3.embedded.sub.vfs.VirtualFileAssembly;
import org.jboss.embedded.api.server.JBossASEmbeddedServer;
import org.jboss.embedded.api.server.JBossASEmbeddedServerFactory;

import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.File;
import java.net.URLClassLoader;
import java.util.Map;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class JBossSubmersibleEJBContainer extends EJBContainer
{
   private JBossASEmbeddedServer server;
   private Context context;

   protected JBossSubmersibleEJBContainer(JBossASEmbeddedServer server, Context context)
   {
      this.server = server;
      this.context = context;
   }

   private static Class<?> cls(ClassLoader loader, String className)
   {
      try
      {
         return loader.loadClass(className);
      }
      catch (ClassNotFoundException e)
      {
         throw new EJBException(e);
      }
   }

   @Override
   public void close()
   {
      try
      {
         server.shutdown();
      }
      catch (Exception e)
      {
         throw new EJBException(e);
      }
   }

   public static EJBContainer createEJBContainer(Map<?, ?> properties, URLClassLoader loader, String jbossHome, String serverName) throws EJBException
   {
      System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");

      File deployments[];
      
      Object modules = property(properties, EJBContainer.MODULES);
      if(modules != null)
      {
         Class<?> componentType = modules.getClass().getComponentType();
         if(componentType == null)
         {
            deployments = new File[1];
            if(modules instanceof String)
               throw new EJBException("EJBTHREE-2221: using String for " + EJBContainer.MODULES + " is NYI");
            else if(modules instanceof File)
               deployments[0] = (File) modules;
            else
               throw new EJBException("EJB 3.1 FR 22.2.2.2: Illegal type " + modules.getClass() + " for " + EJBContainer.MODULES);
         }
         else
         {
            if(componentType.equals(String.class))
               throw new EJBException("EJBTHREE-2221: using String[] for " + EJBContainer.MODULES + " is NYI");
            else if(componentType.equals(File.class))
               deployments = (File[]) modules;
            else
               throw new EJBException("EJB 3.1 FR 22.2.2.2: Illegal component type " + componentType + " for " + EJBContainer.MODULES);
         }
      }
      else
      {
         // ClassPathEjbJarScanner uses TCCL, so we can not modify it yet
         String candidates[] = ClassPathEjbJarScanner.getEjbJars();
         deployments = new File[candidates.length];
         for(int i = 0; i < candidates.length; i++)
            deployments[i] = new File(candidates[i]);
      }

      String appName = property(properties, EJBContainer.APP_NAME, String.class);

      String bindAddress = System.getProperty("embedded.bind.address", "localhost");
      
      Thread.currentThread().setContextClassLoader(loader);

      JBossASEmbeddedServer server = JBossASEmbeddedServerFactory.createServer(loader);
      JBossASServerConfig config = server.getConfiguration();
      config.jbossHome(jbossHome);
      config.serverName(serverName);
      config.bindAddress(bindAddress);
      try
      {
         server.start();

         InitialContext context = new InitialContext();

         if(appName == null)
            server.deploy(deployments);
         else
         {
            /*
            EnterpriseArchive archive = ShrinkWrap.create(EnterpriseArchive.class, appName);
            for(File d : deployments)
               archive.addModule(d);
            server.deploy(archive);
            */
            VirtualFileAssembly assembly = new VirtualFileAssembly(appName + ".ear");
            for(File d : deployments)
            {
               // if it's already a file it must not be mounted twice (see AbstractVFSArchiveStructureDeployer#determineStructure)
               if(d.isFile())
                  assembly.addZip(d.getName(), d);
               else
               {
                  // a whimsical hack to make sure the directory is marked as an expanded archive
                  String name = d.getName();
                  if(!name.endsWith(".jar"))
                     name = name + ".jar";
                  assembly.add(name, d);
               }
            }
            server.deploy(assembly.getMountRoot().toURL());
         }

         return new JBossSubmersibleEJBContainer(server, context);
      }
      catch(Exception e)
      {
         throw new EJBException(e);
      }
   }

   @Override
   public Context getContext()
   {
      return context;
   }

   private static Object property(Map<?, ?> properties, String key)
   {
      if(properties == null)
         return null;
      return properties.get(key);
   }

   private static <T> T property(Map<?, ?> properties, String key, Class<T> expectedType)
   {
      if(properties == null)
         return null;
      // TODO: check expected type
      return expectedType.cast(properties.get(key));
   }
}
