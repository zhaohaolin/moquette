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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.moquette.spi.ISessionsStore;
import org.eclipse.moquette.spi.impl.subscriptions.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author andrea
 */
public class MemorySessionStore implements ISessionsStore {
	private static final Logger				LOG						= LoggerFactory
																			.getLogger(MemorySessionStore.class);
	
	private Map<String, Set<Subscription>>	persistentSubscriptions	= new HashMap<>();
	
	@Override
	public void removeSubscription(String topic, String clientID) {
		LOG.debug("removeSubscription topic filter: {} for clientID: {}",
				topic, clientID);
		if (!persistentSubscriptions.containsKey(clientID)) {
			return;
		}
		Set<Subscription> clientSubscriptions = persistentSubscriptions
				.get(clientID);
		// search for the subscription to remove
		Subscription toBeRemoved = null;
		for (Subscription sub : clientSubscriptions) {
			if (sub.getTopicFilter().equals(topic)) {
				toBeRemoved = sub;
				break;
			}
		}
		
		if (toBeRemoved != null) {
			clientSubscriptions.remove(toBeRemoved);
		}
	}
	
	@Override
	public void addNewSubscription(Subscription newSubscription) {
		final String clientID = newSubscription.getClientId();
		if (!persistentSubscriptions.containsKey(clientID)) {
			persistentSubscriptions.put(clientID, new HashSet<Subscription>());
		}
		
		Set<Subscription> subs = persistentSubscriptions.get(clientID);
		if (!subs.contains(newSubscription)) {
			subs.add(newSubscription);
			persistentSubscriptions.put(clientID, subs);
		}
	}
	
	@Override
	public void wipeSubscriptions(String clientID) {
		persistentSubscriptions.remove(clientID);
	}
	
	@Override
	public void updateSubscriptions(String clientID,
			Set<Subscription> subscriptions) {
		persistentSubscriptions.put(clientID, subscriptions);
	}
	
	@Override
	public boolean contains(String clientID) {
		return persistentSubscriptions.containsKey(clientID);
	}
	
	@Override
	public void createNewSession(String clientID) {
		if (persistentSubscriptions.containsKey(clientID)) {
			LOG.error("already exists a session for client <{}>", clientID);
			return;
		}
		persistentSubscriptions.put(clientID, new HashSet<Subscription>());
	}
	
	@Override
	public List<Subscription> listAllSubscriptions() {
		List<Subscription> allSubscriptions = new ArrayList<Subscription>();
		for (Map.Entry<String, Set<Subscription>> entry : persistentSubscriptions
				.entrySet()) {
			allSubscriptions.addAll(entry.getValue());
		}
		return allSubscriptions;
	}
	
	@Override
	public Set<Subscription> getSubscriptions(String clientID) {
		return persistentSubscriptions.get(clientID);
	}
}
