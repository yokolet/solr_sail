package com.tobedevoured.solrsail;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
   *
 * http://www.apache.org/licenses/LICENSE-2.0
   *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.metapossum.utils.scanner.PackageScanner;

import com.tobedevoured.command.Runner;
import com.tobedevoured.command.annotation.ByYourCommand;
import com.tobedevoured.command.annotation.Command;
import com.tobedevoured.command.annotation.CommandParam;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Solr Config Helper
 * 
 * @author Michael Guymon
 *
 */
@ByYourCommand
public class SolrConfig {
	private static Logger logger = LoggerFactory.getLogger( SolrConfig.class );
	
	private String solrHome;
	
	/**
	 * Create new instance
	 */
	public SolrConfig() {
		loadDefaultConfig();
	}
	
	/**
	 * Create new instance
	 * 
	 * @param solrHome String path
	 */
	public SolrConfig( String solrHome ) {
		if ( solrHome != null ) {
			this.solrHome = solrHome;
		} else {
			loadDefaultConfig();
		}
	}
	
	/**
	 * Load config from TypeSafe {@link ConfigFactory#load()}
	 */
	public void loadDefaultConfig() {
		Config config = ConfigFactory.load();
		solrHome = config.getString("solrsail.solr.home");
	}
	
	public String getSolrHome() {
		return solrHome;
	}
	
	/**
	 * Explode the solr config from the classpath
	 * 
	 * @throws IOException
	 */
	@Command
	public void installFromClasspath() throws IOException {
		logger.info( "Installing config from classpath to {}", this.getSolrHome() );
		for ( String path : findSolrInClasspath() ) {
			if ( path.startsWith( "solr/") && !path.endsWith("/") ) {
				final InputStream inputStream = getClass().getClassLoader().getResourceAsStream( path );
				
				// Remove the solr prefix from the path
				final String destPath = path.substring(5);
				final File destFile = new File( 
					new StringBuilder(solrHome).append( File.separator ).append( destPath ).toString() );
				
				logger.debug( "Copying {} to {}", path, destFile );
				
				if ( !destFile.getParentFile().exists() ) {
					destFile.getParentFile().mkdirs();
				}
				
				final FileOutputStream outputStream = new FileOutputStream( destFile );
				
				IOUtils.copy( inputStream, outputStream ); 
				
				outputStream.close();
				inputStream.close();
			} else {
				logger.debug( "skipping {}", path );
			}
		}
	}
	
	/**
	 * Find Solr dir in the classpath
	 * 
	 * @return Set<String> of paths
	 * @throws IOException 
	 */
	public Set<String> findSolrInClasspath() throws IOException {
		PackageScanner<String> scanner = new PackageScanner<String>( new SolrResourceLoader( this.getClass().getClassLoader() ) );
		scanner.setRecursive( true );
		
		return scanner.scan( "solr" );
	}
	
	/**
	 * Install Solr Config to the local file system
	 * 
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@Command
	public void install() throws IOException, URISyntaxException {
		File jar = new File( this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI() );
		
		if ( jar.toURI().getPath().endsWith( ".jar") && jar.exists() ) {
			installFromJar( jar );
		} else {
			installFromClasspath();
		}
	}
	
	/**
	 * Install Solr Config t the local file system by extracting from the
	 * SolrSail jar
	 * 
	 * @param jar String path
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	@Command
	@CommandParam(name = "jar", type = String.class)
	public void installFromJar( String jar ) throws IOException, URISyntaxException {
		installFromJar( new File( jar ) );
	}
		
	/**
	 * Install Solr Config t the local file system by extracting from the
	 * SolrSail jar
	 * 
	 * @param jar File
	 * @throws IOException
	 */
	public void installFromJar( File jar ) throws IOException {
		logger.info( "Installing config from Jar to {}", this.getSolrHome() );
		logger.debug( "Opening Jar {}", jar.toString() );
		
		JarFile jarFile = new JarFile( jar );
		
		for( Enumeration<JarEntry> enumeration = jarFile.entries(); enumeration.hasMoreElements(); ) {
			JarEntry entry = enumeration.nextElement();
			
			if ( !entry.getName().equals( "solr/") && entry.getName().startsWith( "solr/") ) {
				StringBuilder dest = new StringBuilder( getSolrHome() )
					.append( File.separator )
					.append( entry.getName().replaceFirst("solr/", "") );
				
				File file = new File( dest.toString() );
				
				if ( entry.isDirectory() ) {
					file.mkdirs();
				} else {
					if ( file.getParentFile() != null ) {
						file.getParentFile().mkdirs();
					}
					
					logger.debug( "Copying {} to {}", entry.getName(), dest.toString() );
					
					InputStream input = jarFile.getInputStream( entry );
					Writer writer =  new FileWriter( file.getAbsoluteFile() );
					IOUtils.copy( input, writer );
					
					input.close();
					
					writer.close();
				}
			}
		}
	}
	
	public static void main( String[] args ) throws Exception {
    	Runner.run(args);
    }
}
