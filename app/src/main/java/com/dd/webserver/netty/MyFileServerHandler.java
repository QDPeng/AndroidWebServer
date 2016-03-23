package com.dd.webserver.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpObject;

public class MyFileServerHandler extends
		SimpleChannelInboundHandler<HttpObject> {
	private FileDirectoryHandler directoryHandler;

	public MyFileServerHandler(String url) {
		directoryHandler = new FileDirectoryHandler(url);
	}


	@Override
	protected void messageReceived(ChannelHandlerContext ctx, HttpObject msg)
			throws Exception {
		directoryHandler.onMsgReceived(ctx, msg);

	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
			throws Exception {

		cause.printStackTrace();
		if (ctx.channel().isActive()) {
			ctx.writeAndFlush(cause);
		}
	}



}
