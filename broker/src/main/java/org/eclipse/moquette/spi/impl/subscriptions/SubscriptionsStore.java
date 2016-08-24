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
package org.eclipse.moquette.spi.impl.subscriptions;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.moquette.spi.ISessionsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a tree of topics subscriptions.
 * 
 * @author andrea
 */
public class SubscriptionsStore {
	
	private AtomicReference<TreeNode>	subscriptions	= new AtomicReference<TreeNode>(
																new TreeNode(
																		null));
	private ISessionsStore				sessionsStore;
	private static final Logger			LOG				= LoggerFactory
																.getLogger(SubscriptionsStore.class);
	
	/**
	 * Initialize the subscription tree with the list of subscriptions.
	 * Maintained for compatibility reasons.
	 */
	public void init(final ISessionsStore sessionsStore) {
		LOG.debug("init invoked");
		this.sessionsStore = sessionsStore;
		final List<Subscription> subs = sessionsStore.listAllSubscriptions();
		// reload any subscriptions persisted
		if (LOG.isDebugEnabled()) {
			LOG.debug(
					"Reloading all stored subscriptions...subscription tree before {}",
					dumpTree());
		}
		
		for (Subscription sub : subs) {
			LOG.debug("Re-subscribing {} to topic {}", sub.getClientId(),
					sub.getTopicFilter());
			addDirect(sub);
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Finished loading. Subscription tree after {}",
					dumpTree());
		}
	}
	
	protected void addDirect(Subscription newSub) {
		TreeNode oldRoot;
		NodeCouple couple;
		do {
			oldRoot = subscriptions.get();
			couple = recreatePath(newSub.getTopicFilter(), oldRoot);
			couple.createdNode.addSubscription(newSub); // createdNode
														// could be
														// null?
			// spin lock repeating till we can, swap root, if can't swap just
			// re-do the operation
		} while (!subscriptions.compareAndSet(oldRoot, couple.root));
		LOG.debug("root ref {}, original root was {}", couple.root, oldRoot);
	}
	
	protected NodeCouple recreatePath(String topic, final TreeNode oldRoot) {
		List<Token> tokens = new ArrayList<>();
		try {
			tokens = SubscriptionUtils.parseTopic(topic);
		} catch (ParseException ex) {
			// TODO handle the parse exception
			LOG.error(null, ex);
		}
		
		final TreeNode newRoot = oldRoot.copy();
		TreeNode parent = newRoot;
		TreeNode current = newRoot;
		for (Token token : tokens) {
			TreeNode matchingChildren;
			
			// check if a children with the same token already exists
			if ((matchingChildren = current.childWithToken(token)) != null) {
				// copy the traversed node
				current = matchingChildren.copy();
				current.parent = parent;
				// update the child just added in the children list
				parent.updateChild(matchingChildren, current);
				parent = current;
			} else {
				// create a new node for the newly inserted token
				matchingChildren = new TreeNode(current);
				matchingChildren.setToken(token);
				current.addChild(matchingChildren);
				current = matchingChildren;
			}
		}
		return new NodeCouple(newRoot, current);
	}
	
	public void add(Subscription newSubscription) {
		sessionsStore.addNewSubscription(newSubscription);
		addDirect(newSubscription);
	}
	
	public void removeSubscription(String topic, String clientID) {
		TreeNode oldRoot;
		NodeCouple couple;
		do {
			oldRoot = subscriptions.get();
			couple = recreatePath(topic, oldRoot);
			
			// do the job
			// search for the subscription to remove
			Subscription toBeRemoved = null;
			for (Subscription sub : couple.createdNode.subscriptions()) {
				if (sub.getTopicFilter().equals(topic)
						&& sub.getClientId().equals(clientID)) {
					toBeRemoved = sub;
					break;
				}
			}
			
			if (toBeRemoved != null) {
				couple.createdNode.subscriptions().remove(toBeRemoved);
			}
			// spin lock repeating till we can, swap root, if can't swap just
			// re-do the operation
		} while (!subscriptions.compareAndSet(oldRoot, couple.root));
		sessionsStore.removeSubscription(topic, clientID);
	}
	
	/**
	 * Visit the topics tree to remove matching subscriptions with clientID.
	 * It's a mutating structure operation so create a new subscription tree
	 * (partial or total).
	 */
	public void removeForClient(String clientID) {
		TreeNode oldRoot;
		TreeNode newRoot;
		do {
			oldRoot = subscriptions.get();
			newRoot = oldRoot.removeClientSubscriptions(clientID);
			// spin lock repeating till we can, swap root, if can't swap just
			// re-do the operation
		} while (!subscriptions.compareAndSet(oldRoot, newRoot));
		// persist the update
		sessionsStore.wipeSubscriptions(clientID);
	}
	
	/**
	 * Visit the topics tree to deactivate matching subscriptions with clientID.
	 * It's a mutating structure operation so create a new subscription tree
	 * (partial or total).
	 */
	public void deactivate(String clientID) {
		LOG.debug("Disactivating subscriptions for clientID <{}>", clientID);
		TreeNode oldRoot;
		TreeNode newRoot;
		do {
			oldRoot = subscriptions.get();
			newRoot = oldRoot.deactivate(clientID);
			// spin lock repeating till we can, swap root, if can't swap just
			// re-do the operation
		} while (!subscriptions.compareAndSet(oldRoot, newRoot));
		
		// persist the update
		Set<Subscription> subs = newRoot.findAllByClientID(clientID);
		sessionsStore.updateSubscriptions(clientID, subs);
	}
	
	/**
	 * Visit the topics tree to activate matching subscriptions with clientID.
	 * It's a mutating structure operation so create a new subscription tree
	 * (partial or total).
	 */
	public void activate(String clientID) {
		LOG.debug("Activating subscriptions for clientID <{}>", clientID);
		// sync subscriptions modify by zhaohaolin 20160824
		// remove
		removeForClient(clientID);
		
		// sync subscriptions modify by zhaohaolin 20160706
		// add
		final Set<Subscription> subscriptionSet = sessionsStore
				.getSubscriptions(clientID);
		if (null != subscriptionSet && !subscriptionSet.isEmpty()) {
			for (Subscription subscription : subscriptionSet) {
				addDirect(subscription);
			}
		}
		
		TreeNode oldRoot;
		TreeNode newRoot;
		do {
			oldRoot = subscriptions.get();
			newRoot = oldRoot.activate(clientID);
			// spin lock repeating till we can, swap root, if can't swap just
			// re-do the operation
		} while (!subscriptions.compareAndSet(oldRoot, newRoot));
		
		// persist the update
		Set<Subscription> subs = newRoot.findAllByClientID(clientID);
		sessionsStore.updateSubscriptions(clientID, subs);
	}
	
	/**
	 * Given a topic string return the clients subscriptions that matches it.
	 * Topic string can't contain character # and + because they are reserved to
	 * listeners subscriptions, and not topic publishing.
	 */
	public List<Subscription> matches(String topic) {
		List<Token> tokens;
		try {
			tokens = SubscriptionUtils.parseTopic(topic);
		} catch (ParseException ex) {
			// TODO handle the parse exception
			LOG.error(null, ex);
			return Collections.emptyList();
		}
		
		Queue<Token> tokenQueue = new LinkedBlockingDeque<>(tokens);
		List<Subscription> matchingSubs = new ArrayList<>();
		subscriptions.get().matches(tokenQueue, matchingSubs);
		
		// remove the overlapping subscriptions, selecting ones with greatest
		// qos
		Map<String, Subscription> subsForClient = new HashMap<>();
		for (Subscription sub : matchingSubs) {
			Subscription existingSub = subsForClient.get(sub.getClientId());
			// update the selected subscriptions if not present or if has a
			// greater qos
			if (existingSub == null
					|| existingSub.getRequestedQos().byteValue() < sub
							.getRequestedQos().byteValue()) {
				subsForClient.put(sub.getClientId(), sub);
			}
		}
		
		/* matchingSubs */
		return new ArrayList<Subscription>(subsForClient.values());
	}
	
	public boolean contains(Subscription sub) {
		String topic = sub.getTopicFilter();
		return !matches(topic).isEmpty();
	}
	
	public int size() {
		return subscriptions.get().size();
	}
	
	public String dumpTree() {
		DumpTreeVisitor visitor = new DumpTreeVisitor();
		bfsVisit(subscriptions.get(), visitor, 0);
		return visitor.getResult();
	}
	
	private void bfsVisit(TreeNode node, IVisitor<?> visitor, int deep) {
		if (node == null) {
			return;
		}
		visitor.visit(node, deep);
		for (TreeNode child : node.children) {
			bfsVisit(child, visitor, ++deep);
		}
	}
	
}
