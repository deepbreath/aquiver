/*
 * MIT License
 *
 * Copyright (c) 2019 1619kHz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.aquiver.result.view;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import org.aquiver.Request;
import org.aquiver.RequestContext;
import org.aquiver.ViewHandler;

import java.nio.charset.StandardCharsets;

/**
 * @author WangYi
 * @since 2020/8/22
 */
public abstract class AbstractDataViewHandler implements ViewHandler {
  public abstract String getMimeType(Request request);

  @Override
  public void render(RequestContext ctx, String viewPathName) {
    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK, Unpooled.copiedBuffer(
            viewPathName.getBytes(StandardCharsets.UTF_8)));

    HttpHeaders headers = response.headers();
    headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "x-requested-with,content-type");
    headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST,GET");
    headers.set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    headers.set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
    headers.set(HttpHeaderNames.CONTENT_TYPE, getMimeType(ctx.request()));
    ctx.writeAndFlush(response);
  }
}
