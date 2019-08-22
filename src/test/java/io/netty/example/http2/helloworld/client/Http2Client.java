/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.helloworld.client;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import java.util.concurrent.TimeUnit;

// This is a modified version of netty example.

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged. When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a response.
 */
public final class Http2Client {

  static final String HOST = System.getProperty("host", "127.0.0.1");
  static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));
  static final String URL2 = System.getProperty("url2");

  public static void main(String[] args) {
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    Http2ClientInitializer initializer = new Http2ClientInitializer(Integer.MAX_VALUE);

    try {
      // Configure the client.
      Bootstrap b = new Bootstrap();
      b.group(workerGroup);
      b.channel(NioSocketChannel.class);
      b.option(ChannelOption.SO_KEEPALIVE, true);
      b.remoteAddress(HOST, PORT);
      b.handler(initializer);

      // Start the client.
      Channel channel = b.connect().syncUninterruptibly().channel();
      System.out.println("Connected to [" + HOST + ':' + PORT + ']');

      HttpResponseHandler responseHandler = initializer.responseHandler();
      int streamId = 3;
      HttpScheme scheme = HttpScheme.HTTP;
      AsciiString hostName = new AsciiString(HOST + ':' + PORT);
      System.err.println("Sending request(s)...");
      if (URL2 != null) {
        // Create a simple GET request with an empty body.
        FullHttpRequest request =
            new DefaultFullHttpRequest(HTTP_1_1, GET, URL2, wrappedBuffer(new byte[0]));
        request.headers().add(HttpHeaderNames.HOST, hostName);
        request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
        responseHandler.put(streamId, channel.write(request), channel.newPromise());
      }
      channel.flush();
      responseHandler.awaitResponses(60, TimeUnit.SECONDS);
      System.out.println("Finished HTTP/2 request(s)");

      // Wait until the connection is closed.
      channel.close().syncUninterruptibly();
    } finally {
      workerGroup.shutdownGracefully();
    }
  }
}
