package com.dd.webserver.netty;

import com.dd.webserver.util.FileUtil;
import com.dd.webserver.util.LogUtil;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;



public class NettyServer2 {
	private ChannelFuture future;

	public void run(final int port, final String ip) throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap serverBoot = new ServerBootstrap();
			serverBoot.group(bossGroup, workerGroup)
					.channel(NioServerSocketChannel.class)
					.childHandler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch)
								throws Exception {
							String pathString = FileUtil.getSDCardRoot();
							ch.pipeline().addLast("http-decoder",
									new HttpRequestDecoder());

							ch.pipeline().addLast("http-encoder",
									new HttpResponseEncoder());
							ch.pipeline().addLast("http-chunked",
									new ChunkedWriteHandler());
							ch.pipeline().addLast("UploadHandler",
									new MyFileServerHandler(pathString));

							LogUtil.i("path:" + pathString);
						}
					});
			future = serverBoot.bind(ip, port).sync();
			LogUtil.i("bind ok");
			future.channel().closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	public void closeConnect() {
		if (future != null && future.isCancellable()) {
			future.channel().closeFuture();
		}
	}
}
