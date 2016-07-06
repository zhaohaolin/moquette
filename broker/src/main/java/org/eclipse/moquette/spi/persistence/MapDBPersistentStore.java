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

package org.eclipse.moquette.spi.persistence;

import static org.eclipse.moquette.spi.impl.Utils.defaultGet;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.moquette.proto.MQTTException;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.spi.IMatchingCondition;
import org.eclipse.moquette.spi.IMessagesStore;
import org.eclipse.moquette.spi.ISessionsStore;
import org.eclipse.moquette.spi.impl.events.PublishEvent;
import org.eclipse.moquette.spi.impl.storage.StoredPublishEvent;
import org.eclipse.moquette.spi.impl.subscriptions.Subscription;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MapDB main persistence implementation
 */
public class MapDBPersistentStore implements IMessagesStore, ISessionsStore {
	
	private static final Logger								LOG			= LoggerFactory
																				.getLogger(MapDBPersistentStore.class);
	
	private ConcurrentMap<String, StoredMessage>			retainedStore;
	// maps clientID to the list of pending messages stored
	private ConcurrentMap<String, List<StoredPublishEvent>>	persistentMessageStore;
	// bind clientID+MsgID -> evt message published
	private ConcurrentMap<String, StoredPublishEvent>		inflightStore;
	// map clientID <-> set of currently in flight packet identifiers
	Map<String, Set<Integer>>								inFlightIds;
	// bind clientID+MsgID -> evt message published
	private ConcurrentMap<String, StoredPublishEvent>		qos2Store;
	// persistent Map of clientID, set of Subscriptions
	private ConcurrentMap<String, Set<Subscription>>		persistentSubscriptions;
	private DB												db;
	private String											storePath;
	
	protected final ScheduledExecutorService				scheduler	= Executors
																				.newScheduledThreadPool(1);
	
	/*
	 * The default constructor will create an in memory store as no file path
	 * was specified
	 */
	
	public MapDBPersistentStore() {
		this.storePath = "";
	}
	
	public MapDBPersistentStore(String storePath) {
		this.storePath = storePath;
	}
	
	@Override
	public void initStore() {
		if (this.storePath == null || this.storePath.isEmpty()) {
			this.db = DBMaker.newMemoryDB().make();
		} else {
			File tmpFile;
			try {
				tmpFile = new File(this.storePath);
				boolean fileNewlyCreated = tmpFile.createNewFile();
				LOG.info("Starting with {} [{}] db file",
						fileNewlyCreated ? "fresh" : "existing", storePath);
			} catch (IOException ex) {
				LOG.error(null, ex);
				throw new MQTTException(
						"Can't create temp file for subscriptions storage ["
								+ storePath + "]", ex);
			}
			this.db = DBMaker.newFileDB(tmpFile).make();
		}
		this.retainedStore = db.getHashMap("retained");
		this.persistentMessageStore = db.getHashMap("persistedMessages");
		this.inflightStore = db.getHashMap("inflight");
		this.inFlightIds = db.getHashMap("inflightPacketIDs");
		this.persistentSubscriptions = db.getHashMap("subscriptions");
		this.qos2Store = db.getHashMap("qos2Store");
		this.scheduler.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				db.commit();
			}
		}, 30, 30, TimeUnit.SECONDS);
	}
	
	@Override
	public void cleanRetained(String topic) {
		retainedStore.remove(topic);
	}
	
	@Override
	public void storeRetained(String topic, ByteBuffer message,
			AbstractMessage.QOSType qos) {
		if (!message.hasRemaining()) {
			// clean the message from topic
			retainedStore.remove(topic);
		} else {
			// store the message to the topic
			byte[] raw = new byte[message.remaining()];
			message.get(raw);
			retainedStore.put(topic, new StoredMessage(raw, qos, topic));
		}
	}
	
	@Override
	public Collection<StoredMessage> searchMatching(IMatchingCondition condition) {
		LOG.debug(
				"searchMatching scanning all retained messages, presents are {}",
				retainedStore.size());
		
		List<StoredMessage> results = new ArrayList<>();
		
		for (Map.Entry<String, StoredMessage> entry : retainedStore.entrySet()) {
			StoredMessage storedMsg = entry.getValue();
			if (condition.match(entry.getKey())) {
				results.add(storedMsg);
			}
		}
		
		return results;
	}
	
	@Override
	public void storePublishForFuture(PublishEvent evt) {
		List<StoredPublishEvent> storedEvents;
		String clientID = evt.getClientID();
		if (!persistentMessageStore.containsKey(clientID)) {
			storedEvents = new ArrayList<>();
		} else {
			storedEvents = persistentMessageStore.get(clientID);
		}
		storedEvents.add(convertToStored(evt));
		persistentMessageStore.put(clientID, storedEvents);
		// NB rewind the evt message content
		LOG.debug("Stored published message for client <{}> on topic <{}>",
				clientID, evt.getTopic());
	}
	
	@Override
	public List<PublishEvent> listMessagesInSession(String clientID) {
		List<PublishEvent> liveEvts = new ArrayList<>();
		List<StoredPublishEvent> storedEvts = defaultGet(
				persistentMessageStore, clientID,
				Collections.<StoredPublishEvent> emptyList());
		
		for (StoredPublishEvent storedEvt : storedEvts) {
			liveEvts.add(convertFromStored(storedEvt));
		}
		return liveEvts;
	}
	
	@Override
	public void removeMessageInSession(String clientID, Integer messageID) {
		List<StoredPublishEvent> events = persistentMessageStore.get(clientID);
		if (events == null) {
			return;
		}
		StoredPublishEvent toRemoveEvt = null;
		for (StoredPublishEvent evt : events) {
			if (evt.getMessageID() == null && messageID == null) {
				// was a qos0 message (no ID)
				toRemoveEvt = evt;
			}
			if (evt.getMessageID().equals(messageID)) {
				toRemoveEvt = evt;
			}
		}
		events.remove(toRemoveEvt);
		persistentMessageStore.put(clientID, events);
	}
	
	@Override
	public void dropMessagesInSession(String clientID) {
		persistentMessageStore.remove(clientID);
	}
	
	// ----------------- In flight methods -----------------
	@Override
	public void cleanTemporaryPublish(String clientID, int packetID) {
		String publishKey = String.format("%s%d", clientID, packetID);
		inflightStore.remove(publishKey);
		Set<Integer> inFlightForClient = this.inFlightIds.get(clientID);
		if (inFlightForClient != null) {
			inFlightForClient.remove(packetID);
		}
	}
	
	@Override
	public void storeTemporaryPublish(PublishEvent evt, String clientID,
			int packetID) {
		String publishKey = String.format("%s%d", clientID, packetID);
		StoredPublishEvent storedEvt = convertToStored(evt);
		inflightStore.put(publishKey, storedEvt);
	}
	
	/**
	 * Return the next valid packetIdentifier for the given client session.
	 * */
	@Override
	public int nextPacketID(String clientID) {
		Set<Integer> inFlightForClient = this.inFlightIds.get(clientID);
		if (inFlightForClient == null) {
			int nextPacketId = 1;
			inFlightForClient = new HashSet<>();
			inFlightForClient.add(nextPacketId);
			this.inFlightIds.put(clientID, inFlightForClient);
			return nextPacketId;
		}
		int maxId = inFlightForClient.isEmpty() ? 0 : Collections
				.max(inFlightForClient);
		int nextPacketId = (maxId + 1) % 0xFFFF;
		inFlightForClient.add(nextPacketId);
		return nextPacketId;
	}
	
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
		persistentSubscriptions.put(clientID, clientSubscriptions);
	}
	
	@Override
	public void addNewSubscription(Subscription newSubscription) {
		LOG.debug("addNewSubscription invoked with subscription {}",
				newSubscription);
		final String clientID = newSubscription.getClientId();
		if (!persistentSubscriptions.containsKey(clientID)) {
			LOG.debug(
					"clientID {} is a newcome, creating it's subscriptions set",
					clientID);
			persistentSubscriptions.put(clientID, new HashSet<Subscription>());
		}
		
		Set<Subscription> subs = persistentSubscriptions.get(clientID);
		if (!subs.contains(newSubscription)) {
			LOG.debug(
					"updating clientID {} subscriptions set with new subscription",
					clientID);
			// TODO check the subs doesn't contain another subscription to the
			// same topic with different
			Subscription existingSubscription = null;
			for (Subscription scanSub : subs) {
				if (newSubscription.getTopicFilter().equals(
						scanSub.getTopicFilter())) {
					existingSubscription = scanSub;
					break;
				}
			}
			if (existingSubscription != null) {
				subs.remove(existingSubscription);
			}
			subs.add(newSubscription);
			persistentSubscriptions.put(clientID, subs);
			LOG.debug("clientID {} subscriptions set now is {}", clientID, subs);
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
	public List<Subscription> listAllSubscriptions() {
		List<Subscription> allSubscriptions = new ArrayList<>();
		for (Map.Entry<String, Set<Subscription>> entry : persistentSubscriptions
				.entrySet()) {
			allSubscriptions.addAll(entry.getValue());
		}
		LOG.debug("retrieveAllSubscriptions returning subs {}",
				allSubscriptions);
		return allSubscriptions;
	}
	
	@Override
	public boolean contains(String clientID) {
		return persistentSubscriptions.containsKey(clientID);
	}
	
	@Override
	public void createNewSession(String clientID) {
		LOG.debug("createNewSession for client <{}>", clientID);
		if (persistentSubscriptions.containsKey(clientID)) {
			LOG.error("already exists a session for client <{}>", clientID);
			return;
		}
		LOG.debug(
				"clientID {} is a newcome, creating it's empty subscriptions set",
				clientID);
		persistentSubscriptions.put(clientID, new HashSet<Subscription>());
	}
	
	@Override
	public Set<Subscription> getSubscriptions(String clientID) {
		return persistentSubscriptions.get(clientID);
	}
	
	@Override
	public void close() {
		if (this.db.isClosed()) {
			LOG.debug("already closed");
			return;
		}
		this.db.commit();
		LOG.debug("persisted subscriptions {}", persistentSubscriptions);
		this.db.close();
		LOG.debug("closed disk storage");
		this.scheduler.shutdown();
		LOG.debug("Persistence commit scheduler is shutdown");
	}
	
	/*-------- QoS 2  storage management --------------*/
	@Override
	public void persistQoS2Message(String publishKey, PublishEvent evt) {
		LOG.debug("persistQoS2Message store pubKey: {}, evt: {}", publishKey,
				evt);
		qos2Store.put(publishKey, convertToStored(evt));
	}
	
	@Override
	public void removeQoS2Message(String publishKey) {
		LOG.debug("Removing stored Q0S2 message <{}>", publishKey);
		qos2Store.remove(publishKey);
	}
	
	@Override
	public PublishEvent retrieveQoS2Message(String publishKey) {
		StoredPublishEvent storedEvt = qos2Store.get(publishKey);
		return convertFromStored(storedEvt);
	}
	
	private StoredPublishEvent convertToStored(PublishEvent evt) {
		return new StoredPublishEvent(evt);
	}
	
	private PublishEvent convertFromStored(StoredPublishEvent evt) {
		byte[] message = evt.getMessage();
		ByteBuffer bbmessage = ByteBuffer.wrap(message);
		// bbmessage.flip();
		return new PublishEvent(evt.getTopic(), evt.getQos(), bbmessage,
				evt.isRetain(), evt.getClientID(), evt.getMessageID());
	}
}
