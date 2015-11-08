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

import org.eclipse.moquette.proto.messages.AbstractMessage.QOSType;
import org.eclipse.moquette.proto.messages.PublishMessage;

import java.nio.ByteBuffer;

/**
 * 
 * @author andrea
 */
public class PublishEvent extends MessagingEvent {
	
	private String		topic;
	private QOSType		qos;
	//private  byte[] message;
	private ByteBuffer	message;
	private boolean		retain;
	private String		clientID;
	// Optional attribute, available only fo QoS 1 and 2
	private Integer		msgID;
	
	public PublishEvent(String topic, QOSType qos, ByteBuffer message,
			boolean retain, String clientID, Integer msgID) {
		this.topic = topic;
		this.qos = qos;
		this.message = message;
		this.retain = retain;
		this.clientID = clientID;
		if (qos != QOSType.MOST_ONE) {
			this.msgID = msgID;
		}
	}
	
	public PublishEvent(String clientID, PublishMessage msg) {
		this.clientID = clientID;
		this.topic = msg.getTopicName();
		this.qos = msg.getQos();
		this.message = msg.getPayload();
		this.retain = msg.isRetainFlag();
		if (msg.getQos() != QOSType.MOST_ONE) {
			this.msgID = msg.getMessageID();
		}
	}
	
	public String getTopic() {
		return topic;
	}
	
	public QOSType getQos() {
		return qos;
	}
	
	public ByteBuffer getMessage() {
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
	
	@Override
	public String toString() {
		return "PublishEvent{" + "m_msgID=" + msgID + ", m_clientID='"
				+ clientID + '\'' + ", m_retain=" + retain + ", m_qos=" + qos
				+ ", m_topic='" + topic + '\'' + '}';
	}
}
