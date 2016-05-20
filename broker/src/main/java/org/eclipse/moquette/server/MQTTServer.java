/*
 * CopyRight (c) 2005-2012 Ezviz Co, Ltd. All rights reserved. Filename:
 * MQTTServer.java Creator: joe.zhao Create-Date: 下午5:20:14
 */
package org.eclipse.moquette.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
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
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.eclipse.moquette.commons.Constants;
import org.eclipse.moquette.interception.InterceptHandler;
import org.eclipse.moquette.parser.netty.MQTTDecoder;
import org.eclipse.moquette.parser.netty.MQTTEncoder;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.server.config.MemoryConfig;
import org.eclipse.moquette.server.netty.MoquetteIdleTimoutHandler;
import org.eclipse.moquette.server.netty.NettyMQTTHandler;
import org.eclipse.moquette.server.netty.metrics.BytesMetrics;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsHandler;
import org.eclipse.moquette.server.netty.metrics.MessageMetrics;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsHandler;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.eclipse.moquette.spi.impl.SimpleMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT server
 * 
 * @author joe.zhao
 * @version $Id: MQTTServer, v 0.1 2016年5月18日 下午5:20:14 Exp $
 */
public class MQTTServer {
	
	private final static Logger				LOG						= LoggerFactory
																			.getLogger(MQTTServer.class);
	
	private int								maxRetryCount			= Integer.MAX_VALUE;
	private long							retryTimeout			= 30 * 1000;							// 30s
	private int								port					= 1883;
	private String							ip						= "0.0.0.0";
	private EventLoopGroup					bossGroup;
	private EventLoopGroup					workerGroup;
	private Class<? extends ServerChannel>	channelClass;
	private boolean							useLinuxNativeEpoll		= false;
	
	private BytesMetricsCollector			bytesMetricsCollector	= new BytesMetricsCollector();
	private MessageMetricsCollector			metricsCollector		= new MessageMetricsCollector();
	
	private InterceptHandler				handler;
	
	public synchronized void startServer() throws Exception {
		// start server
		Properties configProps = new Properties();
		
		final IConfig config = new MemoryConfig(configProps);
		
		// set handler
		final ProtocolProcessor processor = SimpleMessaging.getInstance().init(
				config, handler);
		
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
		b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		
		final MoquetteIdleTimoutHandler timeoutHandler = new MoquetteIdleTimoutHandler();
		
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
				
				pipeline.addFirst("idleStateHandler", new IdleStateHandler(0,
						0, Constants.DEFAULT_CONNECT_TIMEOUT));
				pipeline.addAfter("idleStateHandler", "idleEventHandler",
						timeoutHandler);
				
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
		
		int retryCount = 0;
		boolean binded = false;
		do {
			try {
				// Bind and start to accept incoming connections.
				b.bind(new InetSocketAddress(port)).addListener(
						new FutureListener<Void>() {
							
							@Override
							public void operationComplete(Future<Void> future)
									throws Exception {
								LOG.info(
										"MQTT Server started succeed at host=[{}], port=[{}]",
										ip, port);
							}
						});
				
				binded = true;
			} catch (Exception e) {
				LOG.warn("start failed on host:port=> [{}]:[{}], and retry...",
						ip, port);
				retryCount++;
				if (retryCount >= maxRetryCount) {
					throw e;
				}
				
				try {
					TimeUnit.MILLISECONDS.sleep(retryTimeout);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		} while (!binded);
		
		// Bind a shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				stopServer();
			}
		});
	}
	
	public synchronized void stopServer() {
		try {
			if (workerGroup == null) {
				throw new IllegalStateException(
						"Invoked close on an Acceptor that wasn't initialized");
			}
			if (bossGroup == null) {
				throw new IllegalStateException(
						"Invoked close on an Acceptor that wasn't initialized");
			}
			Future<?> workerWaiter = workerGroup.shutdownGracefully();
			Future<?> bossWaiter = bossGroup.shutdownGracefully();
			
			try {
				workerWaiter.await(100);
			} catch (InterruptedException iex) {
				throw new IllegalStateException(iex);
			}
			
			try {
				bossWaiter.await(100);
			} catch (InterruptedException iex) {
				throw new IllegalStateException(iex);
			}
			
			MessageMetrics metrics = metricsCollector.computeMetrics();
			LOG.info("Msg read: {}, msg wrote: {}", metrics.messagesRead(),
					metrics.messagesWrote());
			
			BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
			LOG.info(String.format("Bytes read: %d, bytes wrote: %d",
					bytesMetrics.readBytes(), bytesMetrics.wroteBytes()));
		} catch (Exception e) {
			LOG.error("关闭MQTT服务器失败");
		}
	}
	
	public static void main(String[] args) throws Exception {
		MQTTServer server = new MQTTServer();
		server.startServer();
	}
	
	public boolean isUseLinuxNativeEpoll() {
		return useLinuxNativeEpoll;
	}
	
	public void setUseLinuxNativeEpoll(boolean useLinuxNativeEpoll) {
		this.useLinuxNativeEpoll = useLinuxNativeEpoll;
	}
	
	public void setPort(int port) {
		this.port = port;
	}
	
	public void setIp(String ip) {
		this.ip = ip;
	}
	
	public void setMaxRetryCount(int maxRetryCount) {
		this.maxRetryCount = maxRetryCount;
	}
	
	public void setRetryTimeout(long retryTimeout) {
		this.retryTimeout = retryTimeout;
	}
	
	public InterceptHandler getHandler() {
		return handler;
	}
	
	public void setHandler(InterceptHandler handler) {
		this.handler = handler;
	}
	
}
