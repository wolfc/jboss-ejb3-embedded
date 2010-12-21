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
package org.jboss.ejb3.embedded.sub.test.vfs;

import org.jboss.ejb3.embedded.sub.vfs.VirtualFileAssembly;
import org.jboss.vfs.VirtualFile;
import org.jboss.vfs.util.automount.Automounter;
import org.jboss.vfs.util.automount.MountOption;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This is mostly an collection of hacks to see how VFS3 reacts.
 * 
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class VirtualFileAssemblyTestCase
{
   private static File createDummyXML()
   {
      try
      {
         File file = File.createTempFile("dummy", ".xml");
         PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
         try
         {
            out.println("<dummy/>");
            out.flush();
         }
         finally
         {
            out.close();
         }
         return file;
      }
      catch(IOException e)
      {
         throw new RuntimeException("Failed to create dummy.xml", e);
      }
   }

   /**
    * The EARContentsDeployer will look for the existence of "".
    */
   @Test
   public void testExists() throws Exception
   {
      String appName = "test";

      VirtualFileAssembly assembly = new VirtualFileAssembly(appName);
      
      VirtualFile ear = assembly.getMountRoot().getChild("");
      assertTrue(ear.exists());

      // if a file, it'll be opened as if it is a zip (=> POOF)
      assertFalse(ear.isFile());
   }

   @Test
   public void testMetaInf() throws IOException
   {
      String appName = "test-meta-inf";

      VirtualFileAssembly assembly = new VirtualFileAssembly(appName);
      assembly.addDirectory("META-INF"); // make sure this is visible as a child
      assembly.add("META-INF/dummy.xml", createDummyXML());

      VirtualFile ear = assembly.getMountRoot().getChild("");
      assertTrue(ear.exists());

      assertTrue(ear.getChild("META-INF").exists());
   }
}
