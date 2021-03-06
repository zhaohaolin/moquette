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
package org.eclipse.moquette.server.netty.metrics;

public class MessageMetrics {
	
	private long	messagesRead	= 0;
	private long	messageWrote	= 0;
	
	void incrementRead(long numMessages) {
		messagesRead += numMessages;
	}
	
	void incrementWrote(long numMessages) {
		messageWrote += numMessages;
	}
	
	public long messagesRead() {
		return messagesRead;
	}
	
	public long messagesWrote() {
		return messageWrote;
	}
}
