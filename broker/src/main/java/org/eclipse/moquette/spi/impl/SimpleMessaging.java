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
package org.eclipse.moquette.spi.impl;

import static org.eclipse.moquette.commons.Constants.ACL_FILE_PROPERTY_NAME;
import static org.eclipse.moquette.commons.Constants.ALLOW_ANONYMOUS_PROPERTY_NAME;
import static org.eclipse.moquette.commons.Constants.PASSWORD_FILE_PROPERTY_NAME;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.moquette.commons.Constants;
import org.eclipse.moquette.interception.InterceptHandler;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.spi.IMessagesStore;
import org.eclipse.moquette.spi.ISessionsStore;
import org.eclipse.moquette.spi.ISubscriptionsStore;
import org.eclipse.moquette.spi.impl.security.ACLFileParser;
import org.eclipse.moquette.spi.impl.security.AcceptAllAuthenticator;
import org.eclipse.moquette.spi.impl.security.DenyAllAuthorizator;
import org.eclipse.moquette.spi.impl.security.FileAuthenticator;
import org.eclipse.moquette.spi.impl.security.IAuthenticator;
import org.eclipse.moquette.spi.impl.security.IAuthorizator;
import org.eclipse.moquette.spi.impl.security.PermitAllAuthorizator;
import org.eclipse.moquette.spi.impl.subscriptions.SubscriptionsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Singleton class that orchestrate the execution of the protocol.
 * 
 * Uses the LMAX Disruptor to serialize the incoming, requests, because it work
 * in a evented fashion; the requests income from front Netty connectors and are
 * dispatched to the ProtocolProcessor.
 * 
 * @author andrea
 */
public class SimpleMessaging {
	
	private static final Logger		LOG			= LoggerFactory
														.getLogger(SimpleMessaging.class);
	
	private ISubscriptionsStore		subscriptions;
	private IMessagesStore			storageService;
	private ISessionsStore			sessionsStore;
	
	private BrokerInterceptor		interceptor;
	
	private static SimpleMessaging	INSTANCE;
	
	private final ProtocolProcessor	processor	= new ProtocolProcessor();
	
	private SimpleMessaging() {
	}
	
	public final static SimpleMessaging getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new SimpleMessaging();
		}
		return INSTANCE;
	}
	
	// push message to client
	public void publish(String clientID, PublishMessage msg) {
		processor.executePublish(clientID, msg);
	}
	
	public ProtocolProcessor init(IConfig configProps) {
		subscriptions = new SubscriptionsStore();
		return processInit(configProps, null);
	}
	
	public ProtocolProcessor init(final IConfig configProps,
			final InterceptHandler handler,
			final IMessagesStore storageService,
			final ISessionsStore sessionsStore,
			ISubscriptionsStore subscriptions) {
		this.storageService = storageService;
		this.sessionsStore = sessionsStore;
		this.subscriptions = subscriptions;
		return processInit(configProps, handler);
	}
	
	private ProtocolProcessor processInit(IConfig props,
			InterceptHandler handler) {
		// TODO use a property to select the storage path
		// MapDBPersistentStore mapStorage = new MapDBPersistentStore(
		// props.getProperty(PERSISTENT_STORE_PROPERTY_NAME, ""));
		// storageService = mapStorage;
		// sessionsStore = mapStorage;
		
		storageService.initStore();
		
		List<InterceptHandler> observers = new ArrayList<InterceptHandler>();
		if (null != handler) {
			observers.add(handler);
		}
		
		String interceptorClassName = props.getProperty("intercept.handler");
		if (interceptorClassName != null && !interceptorClassName.isEmpty()) {
			try {
				InterceptHandler handler1 = Class.forName(interceptorClassName)
						.asSubclass(InterceptHandler.class).newInstance();
				observers.add(handler1);
			} catch (Throwable ex) {
				LOG.error("Can't load the intercept handler {}", ex);
			}
		}
		interceptor = new BrokerInterceptor(observers);
		
		subscriptions.init(sessionsStore);
		
		String configPath = System.getProperty("moquette.path", null);
		String authenticatorClassName = props.getProperty(
				Constants.AUTHENTICATOR_CLASS_NAME, "");
		
		IAuthenticator authenticator = null;
		if (!authenticatorClassName.isEmpty()) {
			authenticator = (IAuthenticator) loadClass(authenticatorClassName,
					IAuthenticator.class);
			LOG.info("Loaded custom authenticator {}", authenticatorClassName);
		}
		
		if (authenticator == null) {
			String passwdPath = props.getProperty(PASSWORD_FILE_PROPERTY_NAME,
					"");
			if (passwdPath.isEmpty()) {
				authenticator = new AcceptAllAuthenticator();
			} else {
				authenticator = new FileAuthenticator(configPath, passwdPath);
			}
		}
		
		IAuthorizator authorizator = null;
		String authorizatorClassName = props.getProperty(
				Constants.AUTHORIZATOR_CLASS_NAME, "");
		if (!authorizatorClassName.isEmpty()) {
			authorizator = (IAuthorizator) loadClass(authorizatorClassName,
					IAuthorizator.class);
			LOG.info("Loaded custom authorizator {}", authorizatorClassName);
		}
		
		if (authorizator == null) {
			String aclFilePath = props.getProperty(ACL_FILE_PROPERTY_NAME, "");
			if (aclFilePath != null && !aclFilePath.isEmpty()) {
				authorizator = new DenyAllAuthorizator();
				File aclFile = new File(configPath, aclFilePath);
				try {
					authorizator = ACLFileParser.parse(aclFile);
				} catch (ParseException pex) {
					LOG.error(String.format(
							"Format error in parsing acl file %s", aclFile),
							pex);
				}
				LOG.info("Using acl file defined at path {}", aclFilePath);
			} else {
				authorizator = new PermitAllAuthorizator();
				LOG.info("Starting without ACL definition");
			}
			
		}
		
		boolean allowAnonymous = Boolean.parseBoolean(props.getProperty(
				ALLOW_ANONYMOUS_PROPERTY_NAME, "true"));
		processor.init(subscriptions, storageService, sessionsStore,
				authenticator, allowAnonymous, authorizator, interceptor);
		return processor;
	}
	
	private Object loadClass(String className, Class<?> cls) {
		Object instance = null;
		try {
			Class<?> clazz = Class.forName(className);
			
			// check if method getInstance exists
			Method method = clazz.getMethod("getInstance", new Class[] {});
			try {
				instance = method.invoke(null, new Object[] {});
			} catch (IllegalArgumentException | InvocationTargetException
					| IllegalAccessException ex) {
				LOG.error(null, ex);
				throw new RuntimeException("Cannot call method " + className
						+ ".getInstance", ex);
			}
		} catch (NoSuchMethodException nsmex) {
			try {
				instance = this.getClass().getClassLoader()
						.loadClass(className).asSubclass(cls).newInstance();
			} catch (InstantiationException | IllegalAccessException
					| ClassNotFoundException ex) {
				LOG.error(null, ex);
				throw new RuntimeException(
						"Cannot load custom authenticator class " + className,
						ex);
			}
		} catch (ClassNotFoundException ex) {
			LOG.error(null, ex);
			throw new RuntimeException("Class " + className + " not found", ex);
		} catch (SecurityException ex) {
			LOG.error(null, ex);
			throw new RuntimeException("Cannot call method " + className
					+ ".getInstance", ex);
		}
		
		return instance;
	}
	
	public void shutdown() {
		this.storageService.close();
	}
}
