package com.dd.webserver.netty;

import com.dd.webserver.util.FileUtil;
import com.dd.webserver.util.LogUtil;

import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Pattern;


/***
 * @author lsp
 */
public class FileDirectoryHandler {
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private static final Pattern ALLOWED_FILE_NAME = Pattern
            .compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
    public final String url;
    private FileUploadHandler uploadHandler;

    public FileDirectoryHandler(String url) {
        this.url = url;
        uploadHandler = new FileUploadHandler();
    }

    public void onMsgReceived(ChannelHandlerContext ctx, HttpObject msg)
            throws Exception {
        LogUtil.i("FileDirectoryHandler--->onMsgReceived");
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (request.method().equals(HttpMethod.POST)) {
                uploadHandler.onMsgReceived(ctx, msg);
                return;
            }
            String uri = request.uri();
            if (uri.equals(File.separator)) {
                uri = url;
            }
            final String path = sanitizeUri(uri);
            if (path == null) {
                sendError(ctx, FORBIDDEN);
                LogUtil.i("FileDirectoryHandler--->path == null");
                return;
            }
            File file = new File(path);
            if (file.isHidden() || !file.exists()) {
                sendError(ctx, NOT_FOUND);
                LogUtil.i("FileDirectoryHandler--->file.isHidden() || !file.exists()");
                return;
            }
            if (file.isDirectory()) {
                if (uri.endsWith("/")) {
                    sendListing(ctx, file, uri);
                } else {
                    sendRedirect(ctx, uri + '/');
                }
                return;
            }
            if (!file.isFile()) {
                sendError(ctx, FORBIDDEN);
                LogUtil.i("FileDirectoryHandler--->!file.isFile()");
                return;
            }
            RandomAccessFile randomAccessFile = null;
            try {
                randomAccessFile = new RandomAccessFile(file, "r");
            } catch (FileNotFoundException fnfe) {
                sendError(ctx, NOT_FOUND);
                return;
            }
            long fileLength = randomAccessFile.length();
            HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);

            HttpHeaderUtil.setContentLength(response, fileLength);
            setContentTypeHeader(response, file);
            if (HttpHeaderUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION,
                        HttpHeaderValues.KEEP_ALIVE);
            }
            ctx.write(response);
            ChannelFuture sendFileFuture;
            sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0,
                    fileLength, 8192), ctx.newProgressivePromise());
            sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
                @Override
                public void operationProgressed(
                        ChannelProgressiveFuture future, long progress,
                        long total) {
                    if (total < 0) { // total unknown
                        LogUtil.i("FileDirectoryHandler--->Transfer progress: "
                                + progress);
                    } else {
                        LogUtil.i("FileDirectoryHandler--->Transfer progress: "
                                + progress + " / " + total);
                    }
                }

                @Override
                public void operationComplete(ChannelProgressiveFuture future)
                        throws Exception {
                    LogUtil.i("FileDirectoryHandler--->Transfer complete.");
                }
            });
            ChannelFuture lastContentFuture = ctx
                    .writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            if (!HttpHeaderUtil.isKeepAlive(request)) {
                lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } else if (msg instanceof HttpContent) {
            LogUtil.i("FileDirectoryHandler--> instanceof HttpContent");
            uploadHandler.onMsgReceived(ctx, msg);
        }

    }

    private void setContentTypeHeader(HttpResponse response, File file) {
        String type = FileUtil.getMimeType(file);
        if (type != null)
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, type);
        else
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
    }

    private void sendListing(ChannelHandlerContext ctx, File dir, String uri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE,
                "text/html; charset=UTF-8");
        StringBuilder buf = new StringBuilder();
        String dirPath = dir.getPath();
        buf.append("<!DOCTYPE html>\r\n");
        buf.append("<html><head><title>");
        buf.append(dirPath);
        buf.append("Ŀ¼��");
        buf.append("</title></head><body>\r\n");
        uploadBtn(buf);
        buf.append("<h3>");
        buf.append(dirPath).append(" Ŀ¼��");
        buf.append("</h3>\r\n");
        buf.append("<ul>");
        LogUtil.i("uri:" + uri);
        if (!uri.equals(url + '/'))
            buf.append("<li>���ӣ�<a href=\"../\">..</a></li>\r\n");
        for (File f : dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }
            String name = f.getName();
            // if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
            // continue;
            // }
            if (name.startsWith(".")) {
                continue;
            }
            buf.append("<li><a href=\"");
            buf.append(name);
            buf.append("\">");
            buf.append(name);
            buf.append("</a></li>\r\n");
        }
        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void uploadBtn(StringBuilder responseContent) {
        responseContent
                .append("<FORM ACTION=\"/formpostmultipart\" ENCTYPE=\"multipart/form-data\" METHOD=\"POST\">");
        responseContent
                .append("<input type=hidden name=getform value=\"POST\">");
        responseContent.append("<table border=\"0\">");
        responseContent.append("<tr><td>Select file: <br> "
                + "<input type=file name=\"myfile\" multiple>");
        responseContent.append("</td></tr>");
        responseContent
                .append("<tr><td><INPUT TYPE=\"submit\" NAME=\"Send\" VALUE=\"Send\"></INPUT></td></tr>");
        responseContent.append("</table></FORM>\r\n");

    }

    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
                status, Unpooled.copiedBuffer("Failure: " + status.toString()
                + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE,
                "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }
        if (!uri.startsWith(url)) {
            return null;
        }
        if (!uri.startsWith("/")) {
            return null;
        }
        uri = uri.replace('/', File.separatorChar);
        if (uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator) || uri.startsWith(".")
                || uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()) {
            return null;
        }
        return System.getProperty("user.dir") + File.separator + uri;
    }

    public void clearFiles() {
        if (uploadHandler != null) {
            uploadHandler.clearFiles();
        }
    }

}
