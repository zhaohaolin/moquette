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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.Future;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.eclipse.moquette.commons.Constants;
import org.eclipse.moquette.parser.netty.MQTTDecoder;
import org.eclipse.moquette.parser.netty.MQTTEncoder;
import org.eclipse.moquette.server.ServerAcceptor;
import org.eclipse.moquette.server.config.IConfig;
import org.eclipse.moquette.server.netty.metrics.BytesMetrics;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.BytesMetricsHandler;
import org.eclipse.moquette.server.netty.metrics.MessageMetrics;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsCollector;
import org.eclipse.moquette.server.netty.metrics.MessageMetricsHandler;
import org.eclipse.moquette.spi.impl.ProtocolProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author andrea
 */
public class NettyAcceptor implements ServerAcceptor {
	
	static class WebSocketFrameToByteBufDecoder extends
			MessageToMessageDecoder<BinaryWebSocketFrame> {
		
		@Override
		protected void decode(ChannelHandlerContext chc,
				BinaryWebSocketFrame frame, List<Object> out) throws Exception {
			// convert the frame to a ByteBuf
			ByteBuf bb = frame.content();
			// System.out.println("WebSocketFrameToByteBufDecoder decode - " +
			// ByteBufUtil.hexDump(bb));
			bb.retain();
			out.add(bb);
		}
	}
	
	static class ByteBufToWebSocketFrameEncoder extends
			MessageToMessageEncoder<ByteBuf> {
		
		@Override
		protected void encode(ChannelHandlerContext chc, ByteBuf bb,
				List<Object> out) throws Exception {
			// convert the ByteBuf to a WebSocketFrame
			BinaryWebSocketFrame result = new BinaryWebSocketFrame();
			// System.out.println("ByteBufToWebSocketFrameEncoder encode - " +
			// ByteBufUtil.hexDump(bb));
			result.content().writeBytes(bb);
			out.add(result);
		}
	}
	
	abstract class PipelineInitializer {
		
		abstract void init(ChannelPipeline pipeline) throws Exception;
	}
	
	private static final Logger				LOG						= LoggerFactory
																			.getLogger(NettyAcceptor.class);
	
	private EventLoopGroup					bossGroup;
	private EventLoopGroup					workerGroup;
	private BytesMetricsCollector			bytesMetricsCollector	= new BytesMetricsCollector();
	private MessageMetricsCollector			metricsCollector		= new MessageMetricsCollector();
	
	@Override
	public synchronized void initialize(ProtocolProcessor processor,
			IConfig props) throws IOException {
		bossGroup = new NioEventLoopGroup();
		workerGroup = new NioEventLoopGroup();
		final NettyMQTTHandler handler = new NettyMQTTHandler(processor);
		
		// init mqtt
		initializePlainTCPTransport(handler, props);
		
		// init websocket
		initializeWebSocketTransport(handler, props);
		
		String sslTcpPortProp = props
				.getProperty(Constants.SSL_PORT_PROPERTY_NAME);
		String wssPortProp = props
				.getProperty(Constants.WSS_PORT_PROPERTY_NAME);
		if (sslTcpPortProp != null || wssPortProp != null) {
			SslHandlerFactory sslHandlerFactory = initSSLHandlerFactory(props);
			if (!sslHandlerFactory.canCreate()) {
				LOG.error("Can't initialize SSLHandler layer! Exiting, check your configuration of jks");
				return;
			}
			initializeSSLTCPTransport(handler, props, sslHandlerFactory);
			initializeWSSTransport(handler, props, sslHandlerFactory);
		}
	}
	
	private void initFactory(String host, int port,
			final PipelineInitializer pipeliner) {
		ServerBootstrap b = new ServerBootstrap();
		b.group(bossGroup, workerGroup);
		b.channel(NioServerSocketChannel.class);
		
		// option
		b.option(ChannelOption.SO_BACKLOG, 128);
		b.option(ChannelOption.SO_REUSEADDR, true);
		b.option(ChannelOption.TCP_NODELAY, true);
		b.childOption(ChannelOption.SO_KEEPALIVE, true);
		b.childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
		
		// initalizer
		b.childHandler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				ChannelPipeline pipeline = ch.pipeline();
				try {
					pipeliner.init(pipeline);
				} catch (Throwable th) {
					LOG.error("Severe error during pipeline creation", th);
					throw th;
				}
			}
		});
		try {
			// Bind and start to accept incoming connections.
			ChannelFuture f = b.bind(host, port);
			LOG.info("Server binded host: {}, port: {}", host, port);
			f.sync();
		} catch (InterruptedException ex) {
			LOG.error(null, ex);
		}
	}
	
	private void initializePlainTCPTransport(final NettyMQTTHandler handler,
			IConfig props) throws IOException {
		final MoquetteIdleTimoutHandler timeoutHandler = new MoquetteIdleTimoutHandler();
		String host = props.getProperty(Constants.HOST_PROPERTY_NAME);
		int port = Integer.parseInt(props
				.getProperty(Constants.PORT_PROPERTY_NAME));
		initFactory(host, port, new PipelineInitializer() {
			@Override
			void init(ChannelPipeline pipeline) {
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
	}
	
	private void initializeWebSocketTransport(final NettyMQTTHandler handler,
			IConfig props) throws IOException {
		String webSocketPortProp = props
				.getProperty(Constants.WEB_SOCKET_PORT_PROPERTY_NAME);
		if (webSocketPortProp == null) {
			// Do nothing no WebSocket configured
			LOG.info("WebSocket is disabled");
			return;
		}
		int port = Integer.parseInt(webSocketPortProp);
		
		final MoquetteIdleTimoutHandler timeoutHandler = new MoquetteIdleTimoutHandler();
		
		String host = props.getProperty(Constants.HOST_PROPERTY_NAME);
		initFactory(host, port, new PipelineInitializer() {
			@Override
			void init(ChannelPipeline pipeline) {
				pipeline.addLast("httpEncoder", new HttpResponseEncoder());
				pipeline.addLast("httpDecoder", new HttpRequestDecoder());
				pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
				pipeline.addLast("webSocketHandler",
						new WebSocketServerProtocolHandler("/mqtt",
								"mqtt, mqttv3.1, mqttv3.1.1"));
				pipeline.addLast("ws2bytebufDecoder",
						new WebSocketFrameToByteBufDecoder());
				pipeline.addLast("bytebuf2wsEncoder",
						new ByteBufToWebSocketFrameEncoder());
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
			}
		});
	}
	
	private void initializeSSLTCPTransport(final NettyMQTTHandler handler,
			IConfig props, final SslHandlerFactory sslHandlerFactory)
			throws IOException {
		String sslPortProp = props
				.getProperty(Constants.SSL_PORT_PROPERTY_NAME);
		if (sslPortProp == null) {
			// Do nothing no SSL configured
			LOG.info("SSL is disabled");
			return;
		}
		
		int sslPort = Integer.parseInt(sslPortProp);
		LOG.info("Starting SSL on port {}", sslPort);
		
		final MoquetteIdleTimoutHandler timeoutHandler = new MoquetteIdleTimoutHandler();
		String host = props.getProperty(Constants.HOST_PROPERTY_NAME);
		initFactory(host, sslPort, new PipelineInitializer() {
			@Override
			void init(ChannelPipeline pipeline) throws Exception {
				pipeline.addLast("ssl", sslHandlerFactory.create());
				pipeline.addFirst("idleStateHandler", new IdleStateHandler(0,
						0, Constants.DEFAULT_CONNECT_TIMEOUT));
				pipeline.addAfter("idleStateHandler", "idleEventHandler",
						timeoutHandler);
				// pipeline.addLast("logger", new LoggingHandler("Netty",
				// LogLevel.ERROR));
				pipeline.addFirst("bytemetrics", new BytesMetricsHandler(
						bytesMetricsCollector));
				pipeline.addLast("decoder", new MQTTDecoder());
				pipeline.addLast("encoder", new MQTTEncoder());
				pipeline.addLast("metrics", new MessageMetricsHandler(
						metricsCollector));
				pipeline.addLast("handler", handler);
			}
		});
	}
	
	private void initializeWSSTransport(final NettyMQTTHandler handler,
			IConfig props, final SslHandlerFactory sslHandlerFactory)
			throws IOException {
		String sslPortProp = props
				.getProperty(Constants.WSS_PORT_PROPERTY_NAME);
		if (sslPortProp == null) {
			// Do nothing no SSL configured
			LOG.info("SSL is disabled");
			return;
		}
		int sslPort = Integer.parseInt(sslPortProp);
		final MoquetteIdleTimoutHandler timeoutHandler = new MoquetteIdleTimoutHandler();
		String host = props.getProperty(Constants.HOST_PROPERTY_NAME);
		initFactory(host, sslPort, new PipelineInitializer() {
			@Override
			void init(ChannelPipeline pipeline) throws Exception {
				pipeline.addLast("ssl", sslHandlerFactory.create());
				pipeline.addLast("httpEncoder", new HttpResponseEncoder());
				pipeline.addLast("httpDecoder", new HttpRequestDecoder());
				pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
				pipeline.addLast("webSocketHandler",
						new WebSocketServerProtocolHandler("/mqtt",
								"mqtt mqttv3.1, mqttv3.1.1"));
				pipeline.addLast("ws2bytebufDecoder",
						new WebSocketFrameToByteBufDecoder());
				pipeline.addLast("bytebuf2wsEncoder",
						new ByteBufToWebSocketFrameEncoder());
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
			}
		});
	}
	
	@Override
	public synchronized void close() {
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
	}
	
	public BytesMetricsCollector getBytesMetricsCollector() {
		return bytesMetricsCollector;
	}
	
	public void setBytesMetricsCollector(
			BytesMetricsCollector bytesMetricsCollector) {
		this.bytesMetricsCollector = bytesMetricsCollector;
	}
	
	public MessageMetricsCollector getMetricsCollector() {
		return metricsCollector;
	}
	
	public void setMetricsCollector(MessageMetricsCollector metricsCollector) {
		this.metricsCollector = metricsCollector;
	}
	
	private SslHandlerFactory initSSLHandlerFactory(IConfig props) {
		SslHandlerFactory factory = new SslHandlerFactory(props);
		return factory.canCreate() ? factory : null;
	}
	
	private static class SslHandlerFactory {
		
		private SSLContext	sslContext;
		
		public SslHandlerFactory(IConfig props) {
			this.sslContext = initSSLContext(props);
		}
		
		public boolean canCreate() {
			return this.sslContext != null;
		}
		
		private SSLContext initSSLContext(IConfig props) {
			final String jksPath = props
					.getProperty(Constants.JKS_PATH_PROPERTY_NAME);
			LOG.info("Starting SSL using keystore at {}", jksPath);
			if (jksPath == null || jksPath.isEmpty()) {
				// key_store_password or key_manager_password are empty
				LOG.warn("You have configured the SSL port but not the jks_path, SSL not started");
				return null;
			}
			
			// if we have the port also the jks then keyStorePassword and
			// keyManagerPassword
			// has to be defined
			final String keyStorePassword = props
					.getProperty(Constants.KEY_STORE_PASSWORD_PROPERTY_NAME);
			final String keyManagerPassword = props
					.getProperty(Constants.KEY_MANAGER_PASSWORD_PROPERTY_NAME);
			if (keyStorePassword == null || keyStorePassword.isEmpty()) {
				// key_store_password or key_manager_password are empty
				LOG.warn("You have configured the SSL port but not the key_store_password, SSL not started");
				return null;
			}
			if (keyManagerPassword == null || keyManagerPassword.isEmpty()) {
				// key_manager_password or key_manager_password are empty
				LOG.warn("You have configured the SSL port but not the key_manager_password, SSL not started");
				return null;
			}
			
			try {
				InputStream jksInputStream = jksDatastore(jksPath);
				SSLContext serverContext = SSLContext.getInstance("TLS");
				final KeyStore ks = KeyStore.getInstance("JKS");
				ks.load(jksInputStream, keyStorePassword.toCharArray());
				final KeyManagerFactory kmf = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				kmf.init(ks, keyManagerPassword.toCharArray());
				serverContext.init(kmf.getKeyManagers(), null, null);
				return serverContext;
			} catch (NoSuchAlgorithmException | UnrecoverableKeyException
					| CertificateException | KeyStoreException
					| KeyManagementException | IOException ex) {
				LOG.error("Can't start SSL layer!", ex);
				return null;
			}
		}
		
		private InputStream jksDatastore(String jksPath)
				throws FileNotFoundException {
			URL jksUrl = getClass().getClassLoader().getResource(jksPath);
			if (jksUrl != null) {
				LOG.info("Starting with jks at {}, jks normal {}",
						jksUrl.toExternalForm(), jksUrl);
				return getClass().getClassLoader().getResourceAsStream(jksPath);
			}
			LOG.info("jks not found in bundled resources, try on the filesystem");
			File jksFile = new File(jksPath);
			if (jksFile.exists()) {
				LOG.info("Using {} ", jksFile.getAbsolutePath());
				return new FileInputStream(jksFile);
			}
			LOG.warn("File {} doesn't exists", jksFile.getAbsolutePath());
			return null;
		}
		
		public ChannelHandler create() {
			SSLEngine sslEngine = sslContext.createSSLEngine();
			sslEngine.setUseClientMode(false);
			return new SslHandler(sslEngine);
		}
		
	}
}
