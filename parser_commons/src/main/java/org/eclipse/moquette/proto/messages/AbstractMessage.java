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
package org.eclipse.moquette.proto.messages;

/**
 * Basic abstract message for all MQTT protocol messages.
 * 
 * @author andrea
 */
public abstract class AbstractMessage {
	
	// Client request to connect to Server
	public static final byte	CONNECT		= 1;
	// Connect Acknowledgment
	public static final byte	CONNACK		= 2;
	// Publish message
	public static final byte	PUBLISH		= 3;
	// Publish Acknowledgment
	public static final byte	PUBACK		= 4;
	// Publish Received (assured delivery part 1)
	public static final byte	PUBREC		= 5;
	// Publish Release (assured delivery part 2)
	public static final byte	PUBREL		= 6;
	// Publish Complete (assured delivery part 3)
	public static final byte	PUBCOMP		= 7;
	// Client Subscribe request
	public static final byte	SUBSCRIBE	= 8;
	// Subscribe Acknowledgment
	public static final byte	SUBACK		= 9;
	// Client Unsubscribe request
	public static final byte	UNSUBSCRIBE	= 10;
	// Unsubscribe Acknowledgment
	public static final byte	UNSUBACK	= 11;
	// PING Request
	public static final byte	PINGREQ		= 12;
	// PING Response
	public static final byte	PINGRESP	= 13;
	// Client is Disconnecting
	public static final byte	DISCONNECT	= 14;
	
	public static enum QOSType {
		MOST_ONE, LEAST_ONE, EXACTLY_ONCE, RESERVED, FAILURE;
		
		public static QOSType valueOf(byte qos) {
			switch (qos) {
				case 0x00:
					return MOST_ONE;
				case 0x01:
					return LEAST_ONE;
				case 0x02:
					return EXACTLY_ONCE;
				case (byte) 0x80:
					return FAILURE;
				default:
					throw new IllegalArgumentException(
							"Invalid QOS Type. Expected either 0, 1, 2, or 0x80. Given: "
									+ qos);
			}
		}
		
		public byte byteValue() {
			switch (this) {
				case MOST_ONE:
					return 0;
				case LEAST_ONE:
					return 1;
				case EXACTLY_ONCE:
					return 2;
				case FAILURE:
					return (byte) 0x80;
				default:
					throw new IllegalArgumentException(
							"Cannot give byteValue of QOSType: " + this.name());
			}
		}
		
		public static String formatQoS(QOSType qos) {
			return String.format("%d - %s", qos.byteValue(), qos.name());
		}
	}
	
	// type
	protected boolean	dupFlag;
	protected QOSType	qos;
	protected boolean	retainFlag;
	protected int		remainingLength;
	protected byte		messageType;
	
	public byte getMessageType() {
		return messageType;
	}
	
	public void setMessageType(byte messageType) {
		this.messageType = messageType;
	}
	
	public boolean isDupFlag() {
		return dupFlag;
	}
	
	public void setDupFlag(boolean dupFlag) {
		this.dupFlag = dupFlag;
	}
	
	public QOSType getQos() {
		return qos;
	}
	
	public void setQos(QOSType qos) {
		this.qos = qos;
	}
	
	public boolean isRetainFlag() {
		return retainFlag;
	}
	
	public void setRetainFlag(boolean retainFlag) {
		this.retainFlag = retainFlag;
	}
	
	/**
	 * TOBE used only internally
	 */
	public int getRemainingLength() {
		return remainingLength;
	}
	
	/**
	 * TOBE used only internally
	 */
	public void setRemainingLength(int remainingLength) {
		this.remainingLength = remainingLength;
	}
}
