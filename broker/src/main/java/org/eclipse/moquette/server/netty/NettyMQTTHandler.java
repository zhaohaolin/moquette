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

import static org.eclipse.moquette.proto.messages.AbstractMessage.CONNECT;
import static org.eclipse.moquette.proto.messages.AbstractMessage.DISCONNECT;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PINGREQ;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBACK;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBCOMP;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBLISH;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBREC;
import static org.eclipse.moquette.proto.messages.AbstractMessage.PUBREL;
import static org.eclipse.moquette.proto.messages.AbstractMessage.SUBSCRIBE;
import static org.eclipse.moquette.proto.messages.AbstractMessage.UNSUBSCRIBE;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.CorruptedFrameException;

import org.eclipse.moquette.proto.Utils;
import org.eclipse.moquette.proto.messages.AbstractMessage;
import org.eclipse.moquette.proto.messages.ConnectMessage;
import org.eclipse.moquette.proto.messages.DisconnectMessage;
import org.eclipse.moquette.proto.messages.PingRespMessage;
import org.eclipse.moquette.proto.messages.PubAckMessage;
import org.eclipse.moquette.proto.messages.PubCompMessage;
import org.eclipse.moquette.proto.messages.PubRecMessage;
import org.eclipse.moquette.proto.messages.PubRelMessage;
import org.eclipse.moquette.proto.messages.PublishMessage;
import org.eclipse.moquette.proto.messages.SubscribeMessage;
import org.eclipse.moquette.proto.messages.UnsubscribeMessage;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.events.LostConnectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author andrea
 */
@Sharable
public class NettyMQTTHandler extends ChannelInboundHandlerAdapter {
	
	private static final Logger		LOG	= LoggerFactory
												.getLogger(NettyMQTTHandler.class);
	// private IMessaging messaging;
	private final ProtocolProcessor	processor;
	
	// private NettyMQTTHandler() {
	// this.processor = null;
	// }
	
	public NettyMQTTHandler(ProtocolProcessor processor) {
		this.processor = processor;
	}
	
	@Override
	public void channelRead(final ChannelHandlerContext ctx,
			final Object message) {
		AbstractMessage msg = (AbstractMessage) message;
		LOG.info("Received a message of type {}",
				Utils.msgType2String(msg.getMessageType()));
		try {
			switch (msg.getMessageType()) {
				case CONNECT:
					processor.processConnect(new NettyChannel(ctx),
							(ConnectMessage) msg);
					break;
				case SUBSCRIBE:
					processor.processSubscribe(new NettyChannel(ctx),
							(SubscribeMessage) msg);
					break;
				case UNSUBSCRIBE:
					processor.processUnsubscribe(new NettyChannel(ctx),
							(UnsubscribeMessage) msg);
					break;
				case PUBLISH:
					processor.processPublish(new NettyChannel(ctx),
							(PublishMessage) msg);
					break;
				case PUBREC:
					processor.processPubRec(new NettyChannel(ctx),
							(PubRecMessage) msg);
					break;
				case PUBCOMP:
					processor.processPubComp(new NettyChannel(ctx),
							(PubCompMessage) msg);
					break;
				case PUBREL:
					processor.processPubRel(new NettyChannel(ctx),
							(PubRelMessage) msg);
					break;
				case DISCONNECT:
					processor.processDisconnect(new NettyChannel(ctx),
							(DisconnectMessage) msg);
					break;
				case PUBACK:
					processor.processPubAck(new NettyChannel(ctx),
							(PubAckMessage) msg);
					break;
				case PINGREQ:
					PingRespMessage pingResp = new PingRespMessage();
					ctx.writeAndFlush(pingResp);
					break;
			}
		} catch (Exception ex) {
			LOG.error("Bad error in processing the message", ex);
		}
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		String clientID = (String) NettyUtils.getAttribute(ctx,
				NettyChannel.ATTR_KEY_CLIENTID);
		if (clientID != null && !clientID.isEmpty()) {
			// if the channel was of a correctly connected client, inform
			// messaging
			// else it was of a not completed CONNECT message or sessionStolen
			boolean stolen = false;
			Boolean stolenAttr = (Boolean) NettyUtils.getAttribute(ctx,
					NettyChannel.ATTR_KEY_SESSION_STOLEN);
			if (stolenAttr != null && stolenAttr == Boolean.TRUE) {
				stolen = stolenAttr;
			}
			
			String channelId = ctx.channel().id().asLongText();
			processor.processConnectionLost(new LostConnectionEvent(clientID,
					stolen, channelId));
		}
		ctx.close(/* false */);
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {
		if (cause instanceof CorruptedFrameException) {
			// something goes bad with decoding
			LOG.warn("Error decoding a packet, probably a bad formatted packet, message: "
					+ cause.getMessage());
		} else {
			LOG.error("Ugly error on networking", cause);
		}
		
		// 异常处理
		String clientID = (String) NettyUtils.getAttribute(ctx,
				NettyChannel.ATTR_KEY_CLIENTID);
		if (clientID != null && !clientID.isEmpty()) {
			// if the channel was of a correctly connected client, inform
			// messaging
			// else it was of a not completed CONNECT message or sessionStolen
			boolean stolen = false;
			Boolean stolenAttr = (Boolean) NettyUtils.getAttribute(ctx,
					NettyChannel.ATTR_KEY_SESSION_STOLEN);
			if (stolenAttr != null && stolenAttr == Boolean.TRUE) {
				stolen = stolenAttr;
			}
			
			String channelId = ctx.channel().id().asLongText();
			processor.processConnectionLost(new LostConnectionEvent(clientID,
					stolen, channelId));
		}
		ctx.close();
	}
}
