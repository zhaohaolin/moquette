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
package org.eclipse.moquette.spi.impl.storage;

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.eclipse.moquette.spi.impl.events.PublishEvent;
import org.eclipse.moquette.proto.messages.AbstractMessage.QOSType;

/**
 * Publish event serialized to the DB.
 * 
 * @author andrea
 */
public class StoredPublishEvent implements Serializable {
	
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 1L;
	private String				topic;
	private QOSType				qos;
	private byte[]				message;
	private boolean				retain;
	private String				clientID;
	// Optional attribute, available only fo QoS 1 and 2
	private Integer				msgID;
	
	public StoredPublishEvent(PublishEvent wrapped) {
		this.topic = wrapped.getTopic();
		this.qos = wrapped.getQos();
		this.retain = wrapped.isRetain();
		this.clientID = wrapped.getClientID();
		this.msgID = wrapped.getMessageID();
		
		ByteBuffer buffer = wrapped.getMessage();
		this.message = new byte[buffer.remaining()];
		buffer.get(this.message);
		buffer.rewind();
	}
	
	public String getTopic() {
		return topic;
	}
	
	public QOSType getQos() {
		return qos;
	}
	
	public byte[] getMessage() {
		return message;
	}
	
	public boolean isRetain() {
		return retain;
	}
	
	public String getClientID() {
		return clientID;
	}
	
	public Integer getMessageID() {
		return msgID;
	}
}
