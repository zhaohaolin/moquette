/*
 * Copyright (c) 2012-2015 The original author or authors
 * ------------------------------------------------------ All rights reserved.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Apache License v2.0 which
 * accompanies this distribution.
 * 
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 * 
 * You may elect to redistribute this code under either of these licenses.
 */
package org.eclipse.moquette.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.Properties;

/**
 * Configuration that loads file from the classpath
 * 
 * @author andrea
 */
public class ClasspathConfig implements IConfig {
	
	private static final Logger	LOG	= LoggerFactory
											.getLogger(ClasspathConfig.class);
	
	private final Properties	properties;
	
	public ClasspathConfig() {
		ClassLoader classLoader = Thread.currentThread()
				.getContextClassLoader();
		InputStream is = classLoader
				.getResourceAsStream("config/moquette.conf");
		if (is == null) {
			throw new RuntimeException(
					"Can't locate the resourse \"config/moquette.conf\"");
		}
		Reader configReader = new InputStreamReader(is);
		ConfigurationParser confParser = new ConfigurationParser();
		try {
			confParser.parse(configReader);
		} catch (ParseException pex) {
			LOG.warn(
					"An error occurred in parsing configuration, fallback on default configuration",
					pex);
		}
		properties = confParser.getProperties();
	}
	
	@Override
	public void setProperty(String name, String value) {
		properties.setProperty(name, value);
	}
	
	@Override
	public String getProperty(String name) {
		return properties.getProperty(name);
	}
	
	@Override
	public String getProperty(String name, String defaultValue) {
		return properties.getProperty(name, defaultValue);
	}
}
