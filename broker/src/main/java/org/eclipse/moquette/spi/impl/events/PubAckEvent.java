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

/**
 * Used to send the ack message back to the client after a publish
 */
public class PubAckEvent extends MessagingEvent {
	
	private int		messageId;
	private String	clientID;
	
	public PubAckEvent(int messageID, String clientID) {
		this.messageId = messageID;
		this.clientID = clientID;
	}
	
	public int getMessageId() {
		return messageId;
	}
	
	public String getClientID() {
		return clientID;
	}
}
