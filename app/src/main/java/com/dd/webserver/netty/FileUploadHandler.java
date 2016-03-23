package com.dd.webserver.netty;

import com.dd.webserver.util.FileUtil;
import com.dd.webserver.util.LogUtil;

import static io.netty.buffer.Unpooled.copiedBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.DiskAttribute;
import io.netty.handler.codec.http.multipart.DiskFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.EndOfDataDecoderException;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;



/**
 * �����ļ��ϴ�����
 * 
 * @author lsp
 * 
 */
public class FileUploadHandler {
	private final StringBuilder responseContent = new StringBuilder();
	private static final HttpDataFactory factory = new DefaultHttpDataFactory(
			DefaultHttpDataFactory.MAXSIZE); // Disk if size exceed
	private HttpPostRequestDecoder decoder;
	private HttpRequest request;
	static {
		// should delete file on exit (in normal exit)
		DiskFileUpload.deleteOnExitTemporaryFile = true;
		DiskFileUpload.baseDirectory = null; // system temp directory
		// should delete file on exit (in normal exit)
		DiskAttribute.deleteOnExitTemporaryFile = true;
		DiskAttribute.baseDirectory = null; // system temp directory
	}

	public void onMsgReceived(ChannelHandlerContext ctx, HttpObject msg)
			throws Exception {
		if (msg instanceof HttpRequest) {
			HttpRequest request = this.request = (HttpRequest) msg;
			LogUtil.i("FileUploadHandler--->onMsgReceived");

			responseContent.setLength(0);
			try {
				decoder = new HttpPostRequestDecoder(factory, request);
			} catch (ErrorDataDecoderException e1) {
				e1.printStackTrace();
				responseContent.append(e1.getMessage());
				writeResponse(ctx.channel());
				ctx.channel().close();
				return;
			}
		}

		if (decoder != null) {
			if (msg instanceof HttpContent) {
				// New chunk is received
				HttpContent chunk = (HttpContent) msg;
				try {
					decoder.offer(chunk);
				} catch (ErrorDataDecoderException e1) {
					e1.printStackTrace();
					responseContent.append(e1.getMessage());
					writeResponse(ctx.channel());
					ctx.channel().close();
					return;
				}

				readHttpDataChunkByChunk();
				// example of reading only if at the end
				if (chunk instanceof LastHttpContent) {
					writeResponse(ctx.channel());
					reset();
				}
			}
		} else {
			if (this.request == null) {
				ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
			} else {
				writeResponse(ctx.channel());
			}

		}

	}

	private void readHttpDataChunkByChunk() {
		try {
			while (decoder.hasNext()) {
				InterfaceHttpData data = decoder.next();
				if (data != null) {
					try {
						// new value
						writeHttpData(data);
					} finally {
						data.release();
					}
				}
			}
		} catch (EndOfDataDecoderException e1) {
			// end
			responseContent
					.append("\r\n\r\nEND OF CONTENT CHUNK BY CHUNK\r\n\r\n");
		}
	}

	private void reset() {
		this.decoder.destroy();
		this.decoder = null;
		this.request = null;
	}

	private void writeResponse(Channel channel) {
		ByteBuf buf = copiedBuffer(responseContent.toString(),
				CharsetUtil.UTF_8);
		responseContent.setLength(0);
		boolean close = request.headers().contains(HttpHeaderNames.CONNECTION,
				HttpHeaderValues.CLOSE, true)
				|| request.protocolVersion().equals(HttpVersion.HTTP_1_0)
				&& !request.headers().contains(HttpHeaderNames.CONNECTION,
						HttpHeaderValues.KEEP_ALIVE, true);
		FullHttpResponse response = new DefaultFullHttpResponse(
				HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
		response.headers().set(HttpHeaderNames.CONTENT_TYPE,
				"text/plain; charset=UTF-8");

		if (!close) {
			response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH,
					buf.readableBytes());
		}
		ChannelFuture future = channel.writeAndFlush(response);
		if (close) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private void writeHttpData(InterfaceHttpData data) {
		if (data.getHttpDataType() == HttpDataType.FileUpload) {
			FileUpload fileUpload = (FileUpload) data;
			try {
				saveFile(fileUpload.getFile(), fileUpload.getFilename());
				responseContent.append("\r\nFile Upload Success: "
						+ fileUpload.getFilename() + "\r\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void saveFile(File srcFile, String name) {
		try {
			FileInputStream fis = new FileInputStream(srcFile);
			File file = new File(FileUtil.getAppPath() + File.separator + name);
			FileOutputStream fos = new FileOutputStream(file);
			byte[] content = new byte[1024];
			int ret = -1;
			while ((ret = fis.read(content)) != -1) {
				fos.write(content, 0, ret);
			}

			fos.flush();
			fos.close();
			fis.close();
			LogUtil.i("FileUploadHandler--->savedFile" + name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void clearFiles() {
		if (decoder != null) {
			decoder.cleanFiles();
		}
	}

}
