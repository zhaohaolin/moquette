/*
 * CopyRight (c) 2005-2012 Ezviz Co, Ltd. All rights reserved. Filename:
 * MQTTServer.java Creator: joe.zhao Create-Date: 下午5:20:14
 */
package org.eclipse.moquette.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Properties;

import org.eclipse.moquette.commons.Constants;
import org.eclipse.moquette.parser.netty.MQTTDecoder;
import org.eclipse.moquette.parser.netty.MQTTEncoder;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.server.config.MemoryConfig;
import org.eclipse.moquette.server.netty.NettyMQTTHandler;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsHandler;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsHandler;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.SimpleMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO
 * 
 * @author joe.zhao
 * @version $Id: MQTTServer, v 0.1 2016年5月18日 下午5:20:14 Exp $
 */
public class MQTTServer {
	
	private final static Logger				LOG						= LoggerFactory
																			.getLogger(MQTTServer.class);
	private int								port					= 1883;
	private String							ip						= "0.0.0.0";
	private EventLoopGroup					bossGroup;
	private EventLoopGroup					workerGroup;
	private Class<? extends ServerChannel>	channelClass;
	private boolean							useLinuxNativeEpoll		= false;
	
	private BytesMetricsCollector			bytesMetricsCollector	= new BytesMetricsCollector();
	private MessageMetricsCollector			metricsCollector		= new MessageMetricsCollector();
	
	public synchronized void startServer() {
		try {
			// start server
			Properties configProps = new Properties();
			final IConfig config = new MemoryConfig(configProps);
			final ProtocolProcessor processor = SimpleMessaging.getInstance()
					.init(config);
			
			if (useLinuxNativeEpoll) {
				bossGroup = new EpollEventLoopGroup();
				workerGroup = new EpollEventLoopGroup();
				channelClass = EpollServerSocketChannel.class;
			} else {
				bossGroup = new NioEventLoopGroup();
				workerGroup = new NioEventLoopGroup();
				channelClass = NioServerSocketChannel.class;
			}
			
			final NettyMQTTHandler handler = new NettyMQTTHandler(processor);
			
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup);
			b.channel(channelClass);
			
			// option
			b.option(ChannelOption.SO_BACKLOG, 128);
			b.option(ChannelOption.SO_REUSEADDR, true);
			b.option(ChannelOption.TCP_NODELAY, true);
			b.childOption(ChannelOption.SO_KEEPALIVE, true);
			b.childOption(ChannelOption.ALLOCATOR,
					PooledByteBufAllocator.DEFAULT);
			
			// initalizer
			b.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ChannelPipeline pipeline = ch.pipeline();
					
					// -------------------
					// 靠近传输层
					// -------------------
					// InternalLoggerFactory
					// .setDefaultFactory(new Slf4JLoggerFactory());
					pipeline.addLast("logger", new LoggingHandler("Netty",
							LogLevel.DEBUG));
					
					pipeline.addFirst("idleStateHandler", new IdleStateHandler(
							0, 0, Constants.DEFAULT_CONNECT_TIMEOUT));
					pipeline.addAfter("idleStateHandler", "idleEventHandler",
							new MoquetteIdleTimoutHandler());
					
					pipeline.addFirst("bytemetrics", new BytesMetricsHandler(
							bytesMetricsCollector));
					pipeline.addLast("decoder", new MQTTDecoder());
					pipeline.addLast("encoder", new MQTTEncoder());
					pipeline.addLast("metrics", new MessageMetricsHandler(
							metricsCollector));
					pipeline.addLast("handler", handler);
					// -------------------
					// 靠近应用层
					// -------------------
				}
			});
			try {
				// Bind and start to accept incoming connections.
				ChannelFuture f = b.bind(port);
				LOG.info("Server binded host: {}, port: {}", ip, port);
				f.sync();
			} catch (InterruptedException ex) {
				LOG.error(null, ex);
			}
			
			// Bind a shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					stopServer();
				}
			});
		} catch (Exception e) {
			LOG.error("开始MQTT服务器失败.", e);
		}
	}
	
	public synchronized void stopServer() {
		//
	}
	
}
