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
 */package org.eclipse.moquette.spi.impl;

import io.netty.util.AttributeKey;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.server.ServerChannel;

/**
 * This a synchronous channel that avoid output ring buffer from Processor
 */
class MockReceiverChannel implements ServerChannel {
	// byte m_returnCode;
	AbstractMessage				m_receivedMessage;
	private Map<Object, Object>	m_attributes	= new HashMap<>();
	
	@Override
	public Object getAttribute(AttributeKey<Object> key) {
		return m_attributes.get(key);
	}
	
	@Override
	public void setAttribute(AttributeKey<Object> key, Object value) {
		m_attributes.put(key, value);
	}
	
	@Override
	public void setIdleTime(int idleTime) {
	}
	
	@Override
	public void close(boolean immediately) {
	}
	
	@Override
	public String channelId() {
		return "";
	}
	
	public AbstractMessage getMessage() {
		return this.m_receivedMessage;
	}
	
	// public byte getReturnCode() {
	// return this.m_returnCode;
	// }
	
	@Override
	public void write(Object value) {
		try {
			this.m_receivedMessage = (AbstractMessage) value;
			// if (this.m_receivedMessage instanceof PublishMessage) {
			// T buf = (T) this.m_receivedMessage;
			// }
		} catch (Exception ex) {
			throw new AssertionError("Wrong return code");
		}
	}
}
