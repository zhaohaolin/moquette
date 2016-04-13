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

import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

class TreeNode {
	
	private class ClientIDComparator implements Comparator<Subscription> {
		
		public int compare(Subscription o1, Subscription o2) {
			return o1.getClientId().compareTo(o2.getClientId());
		}
		
	}
	
	TreeNode			parent;
	Token				token;
	List<TreeNode>		children		= new ArrayList<TreeNode>();
	List<Subscription>	subscriptions	= new ArrayList<Subscription>();
	
	TreeNode(TreeNode parent) {
		this.parent = parent;
	}
	
	Token getToken() {
		return token;
	}
	
	void setToken(Token topic) {
		this.token = topic;
	}
	
	void addSubscription(Subscription s) {
		// avoid double registering for same clientID, topic and QoS
		if (subscriptions.contains(s)) {
			return;
		}
		// remove existing subscription for same client and topic but different
		// QoS
		Comparator<Subscription> comparator = new ClientIDComparator();
		Collections.sort(subscriptions, comparator);
		int existingSubIdx = Collections.binarySearch(subscriptions, s,
				comparator);
		if (existingSubIdx >= 0) {
			subscriptions.remove(existingSubIdx);
		}
		
		subscriptions.add(s);
	}
	
	void addChild(TreeNode child) {
		children.add(child);
	}
	
	/**
	 * Creates a shallow copy of the current node. Copy the token and the
	 * children.
	 * */
	TreeNode copy() {
		final TreeNode copy = new TreeNode(this);
		copy.parent = parent;
		copy.children = new ArrayList<>(children);
		copy.subscriptions = new ArrayList<>(subscriptions.size());
		for (Subscription sub : subscriptions) {
			copy.subscriptions.add(new Subscription(sub));
		}
		copy.token = token;
		return copy;
	}
	
	/**
	 * Search for children that has the specified token, if not found return
	 * null;
	 */
	TreeNode childWithToken(Token token) {
		for (TreeNode child : children) {
			if (child.getToken().equals(token)) {
				return child;
			}
		}
		
		return null;
	}
	
	void updateChild(TreeNode oldChild, TreeNode newChild) {
		children.remove(oldChild);
		children.add(newChild);
	}
	
	List<Subscription> subscriptions() {
		return subscriptions;
	}
	
	void matches(Queue<Token> tokens, List<Subscription> matchingSubs) {
		Token t = tokens.poll();
		
		// check if t is null <=> tokens finished
		if (t == null) {
			matchingSubs.addAll(subscriptions);
			// check if it has got a MULTI child and add its subscriptions
			for (TreeNode n : children) {
				if (n.getToken() == Token.MULTI || n.getToken() == Token.SINGLE) {
					matchingSubs.addAll(n.subscriptions());
				}
			}
			
			return;
		}
		
		// we are on MULTI, than add subscriptions and return
		if (token == Token.MULTI) {
			matchingSubs.addAll(subscriptions);
			return;
		}
		
		for (TreeNode n : children) {
			if (n.getToken().match(t)) {
				// Create a copy of token, else if navigate 2 sibling it
				// consumes 2 elements on the queue instead of one
				n.matches(new LinkedBlockingQueue<>(tokens), matchingSubs);
				// TODO don't create a copy n.matches(tokens, matchingSubs);
			}
		}
	}
	
	/**
	 * Return the number of registered subscriptions
	 */
	int size() {
		int res = subscriptions.size();
		for (TreeNode child : children) {
			res += child.size();
		}
		return res;
	}
	
	/**
	 * Create a copied subtree rooted on this node but purged of clientID's
	 * subscriptions.
	 * */
	TreeNode removeClientSubscriptions(String clientID) {
		// collect what to delete and then delete to avoid
		// ConcurrentModification
		TreeNode newSubRoot = this.copy();
		List<Subscription> subsToRemove = new ArrayList<>();
		for (Subscription s : newSubRoot.subscriptions) {
			if (s.getClientId().equals(clientID)) {
				subsToRemove.add(s);
			}
		}
		
		for (Subscription s : subsToRemove) {
			newSubRoot.subscriptions.remove(s);
		}
		
		// go deep
		List<TreeNode> newChildren = new ArrayList<>(newSubRoot.children.size());
		for (TreeNode child : newSubRoot.children) {
			newChildren.add(child.removeClientSubscriptions(clientID));
		}
		newSubRoot.children = newChildren;
		return newSubRoot;
	}
	
	/**
	 * Deactivate all topic subscriptions for the given clientID.
	 * */
	TreeNode deactivate(String clientID) {
		TreeNode newSubRoot = this.copy();
		for (Subscription s : newSubRoot.subscriptions) {
			if (s.getClientId().equals(clientID)) {
				s.setActive(false);
			}
		}
		
		// go deep
		List<TreeNode> newChildren = new ArrayList<>(newSubRoot.children.size());
		for (TreeNode child : newSubRoot.children) {
			newChildren.add(child.deactivate(clientID));
		}
		newSubRoot.children = newChildren;
		return newSubRoot;
	}
	
	/**
	 * Activate all topic subscriptions for the given clientID.
	 * */
	TreeNode activate(String clientID) {
		TreeNode newSubRoot = this.copy();
		for (Subscription s : newSubRoot.subscriptions) {
			if (s.getClientId().equals(clientID)) {
				s.setActive(true);
			}
		}
		
		// go deep
		List<TreeNode> newChildren = new ArrayList<>(newSubRoot.children.size());
		for (TreeNode child : newSubRoot.children) {
			newChildren.add(child.activate(clientID));
		}
		newSubRoot.children = newChildren;
		return newSubRoot;
	}
	
	/**
	 * @return the set of subscriptions for the given client.
	 * */
	Set<Subscription> findAllByClientID(String clientID) {
		Set<Subscription> subs = new HashSet<>();
		for (Subscription s : subscriptions) {
			if (s.getClientId().equals(clientID)) {
				subs.add(s);
			}
		}
		// go deep
		for (TreeNode child : children) {
			subs.addAll(child.findAllByClientID(clientID));
		}
		return subs;
	}
}
