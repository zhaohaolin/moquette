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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.events.LostConnectionEvent;

@Sharable
public class MoquetteIdleTimoutHandler extends ChannelDuplexHandler {
	
	// private IMessaging messaging;
	private final ProtocolProcessor	processor;
	
	public MoquetteIdleTimoutHandler(ProtocolProcessor processor) {
		this.processor = processor;
	}
	
	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
			throws Exception {
		if (evt instanceof IdleStateEvent) {
			IdleState e = ((IdleStateEvent) evt).state();
			if (e == IdleState.ALL_IDLE) {
				String clientID = (String) NettyUtils.getAttribute(ctx,
						NettyChannel.ATTR_KEY_CLIENTID);
				if (clientID != null && !clientID.isEmpty()) {
					// if the channel was of a correctly connected client,
					// inform
					// messaging
					// else it was of a not completed CONNECT message or
					// sessionStolen
					boolean stolen = false;
					Boolean stolenAttr = (Boolean) NettyUtils.getAttribute(ctx,
							NettyChannel.ATTR_KEY_SESSION_STOLEN);
					if (stolenAttr != null && stolenAttr == Boolean.TRUE) {
						stolen = stolenAttr;
					}
					
					String channelId = ctx.channel().id().asLongText();
					processor.processConnectionLost(new LostConnectionEvent(
							clientID, stolen, channelId));
				}
				// fire a channelInactive to trigger publish of Will
				ctx.fireChannelInactive();
				ctx.close();
			}
		} else {
			super.userEventTriggered(ctx, evt);
		}
	}
}
