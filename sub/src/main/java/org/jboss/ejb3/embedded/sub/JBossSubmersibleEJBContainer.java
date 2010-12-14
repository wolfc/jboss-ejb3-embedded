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

import org.jboss.ejb3.embedded.impl.base.scanner.ClassPathEjbJarScanner;
import org.jboss.embedded.api.server.JBossASEmbeddedServer;
import org.jboss.embedded.api.server.JBossASEmbeddedServerFactory;

import javax.ejb.EJBException;
import javax.ejb.embeddable.EJBContainer;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.File;
import java.net.URLClassLoader;
import java.util.Arrays;
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

      // ClassPathEjbJarScanner uses TCCL, so we can not modify it yet
      String modules[] = ClassPathEjbJarScanner.getEjbJars();
      System.err.println("modules = " + Arrays.toString(modules));

      Thread.currentThread().setContextClassLoader(loader);

      JBossASEmbeddedServer server = JBossASEmbeddedServerFactory.createServer(loader);
      server.getConfiguration().jbossHome(jbossHome);
      server.getConfiguration().serverName(serverName);
      try
      {
         server.start();

         InitialContext context = new InitialContext();

         for(String m : modules)
            server.deploy(new File(m));

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
}
