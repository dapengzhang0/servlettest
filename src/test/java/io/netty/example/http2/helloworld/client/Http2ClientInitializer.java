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

import static io.netty.handler.logging.LogLevel.INFO;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;

// This is a modified version of netty example.

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */
public class Http2ClientInitializer extends ChannelInitializer<SocketChannel> {
  private static final Http2FrameLogger logger = new Http2FrameLogger(INFO, Http2ClientInitializer.class);

  private final int maxContentLength;
  private Http2ConnectionHandler connectionHandler;
  private HttpResponseHandler responseHandler;

  public Http2ClientInitializer(int maxContentLength) {
    this.maxContentLength = maxContentLength;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    final Http2Connection connection = new DefaultHttp2Connection(false);

    Http2Settings settings = new Http2Settings();
    settings.initialWindowSize(1024 * 1024);

    connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
        .frameListener(new DelegatingDecompressorFrameListener(
            connection,
            new InboundHttp2ToHttpAdapterBuilder(connection)
                .maxContentLength(maxContentLength)
                .propagateSettings(true)
                .build()))
        .frameLogger(logger)
        .connection(connection)
        .initialSettings(settings)
        .build();

    responseHandler = new HttpResponseHandler();
    ch.pipeline().addLast(connectionHandler, responseHandler,
        new UserEventLogger(connectionHandler));
  }

  public HttpResponseHandler responseHandler() {
    return responseHandler;
  }

  /**
   * Class that logs any User Events triggered on this channel.
   */
  private static class UserEventLogger extends ChannelInboundHandlerAdapter {
    Http2ConnectionHandler connectionHandler;

    UserEventLogger(Http2ConnectionHandler connectionHandler) {
      this.connectionHandler = connectionHandler;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
      System.out.println("User Event Triggered: " + evt);
      ctx.fireUserEventTriggered(evt);
      if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
        Http2Stream connectionStream = connectionHandler.connection().connectionStream();
        int currentSize = connectionHandler.connection().local().flowController()
            .windowSize(connectionStream);

        try {
          connectionHandler.decoder().flowController()
              .incrementWindowSize(connectionStream, 1024 * 1024 - currentSize);
        } catch (Http2Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
