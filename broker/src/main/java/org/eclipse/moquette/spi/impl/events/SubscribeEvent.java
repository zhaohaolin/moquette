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
package org.eclipse.moquette.spi.impl.events;

import org.eclipse.moquette.spi.impl.subscriptions.Subscription;

/**
 * 
 * @author andrea
 */
public class SubscribeEvent extends MessagingEvent {
	
	private Subscription	subscription;
	private int				messageID;
	
	public SubscribeEvent(Subscription subscription, int messageID) {
		this.subscription = subscription;
		this.messageID = messageID;
	}
	
	public Subscription getSubscription() {
		return subscription;
	}
	
	public int getMessageID() {
		return messageID;
	}
}
