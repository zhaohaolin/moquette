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
package org.eclipse.moquette.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import org.eclipse.moquette.server.Constants;
import org.eclipse.moquette.server.ServerChannel;

/**
 * 
 * @author andrea
 */
public class NettyChannel implements ServerChannel {
	
	private ChannelHandlerContext				ctx;
	
	public static final String					ATTR_USERNAME			= "username";
	public static final String					ATTR_SESSION_STOLEN		= "sessionStolen";
	
	public static final AttributeKey<Object>	ATTR_KEY_KEEPALIVE		= AttributeKey
																				.valueOf(Constants.KEEP_ALIVE);
	public static final AttributeKey<Object>	ATTR_KEY_CLEANSESSION	= AttributeKey
																				.valueOf(Constants.CLEAN_SESSION);
	public static final AttributeKey<Object>	ATTR_KEY_CLIENTID		= AttributeKey
																				.valueOf(Constants.ATTR_CLIENTID);
	public static final AttributeKey<Object>	ATTR_KEY_USERNAME		= AttributeKey
																				.valueOf(ATTR_USERNAME);
	public static final AttributeKey<Object>	ATTR_KEY_SESSION_STOLEN	= AttributeKey
																				.valueOf(ATTR_SESSION_STOLEN);
	
	NettyChannel(ChannelHandlerContext ctx) {
		this.ctx = ctx;
	}
	
	@Override
	public Object getAttribute(AttributeKey<Object> key) {
		Attribute<Object> attr = ctx.attr(key);
		return attr.get();
	}
	
	@Override
	public void setAttribute(AttributeKey<Object> key, Object value) {
		Attribute<Object> attr = ctx.attr(key);
		attr.set(value);
	}
	
	@Override
	public void setIdleTime(int idleTime) {
		if (ctx.pipeline().names().contains("idleStateHandler")) {
			ctx.pipeline().remove("idleStateHandler");
		}
		ctx.pipeline().addFirst("idleStateHandler",
				new IdleStateHandler(0, 0, idleTime));
	}
	
	@Override
	public void close(boolean immediately) {
		ctx.close();
	}
	
	@Override
	public void write(Object value) {
		ctx.writeAndFlush(value);
	}
	
	@Override
	public String channelId() {
		return this.ctx.channel().id().asLongText();
	}
	
	@Override
	public String toString() {
		String clientID = (String) getAttribute(ATTR_KEY_CLIENTID);
		return "session [clientID: " + clientID + "]" + super.toString();
	}
}
