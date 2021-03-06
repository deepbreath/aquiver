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
package org.aquiver.mvc.router;

import org.aquiver.RequestContext;
import org.aquiver.RequestHandler;
import org.aquiver.RouteRepeatException;
import org.aquiver.mvc.annotation.*;
import org.aquiver.mvc.router.views.PebbleHTMLView;
import org.aquiver.mvc.router.views.ViewType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author WangYi
 * @since 2020/5/23
 */
public final class RestfulRouter implements Router {
  private static final Logger log = LoggerFactory.getLogger(RestfulRouter.class);

  private final Map<String, RouteInfo> routes = new ConcurrentHashMap<>(64);
  private final MethodHandles.Lookup lookup = MethodHandles.lookup();

  /**
   * Get Route Map
   *
   * @return Route Map
   */
  public Map<String, RouteInfo> getRoutes() {
    return this.routes;
  }

  @Override
  public void registerRoute(String path, Object object) {
    try {
      Class<?> cls = object.getClass();
      Method[] methods = cls.getMethods();
      for (Method method : methods) {
        Path methodPath = method.getAnnotation(Path.class);
        if (Objects.nonNull(methodPath)) {
          String completeUrl = this.getMethodUrl(path, methodPath.value());
          this.registerRoute(cls, object, completeUrl, method, methodPath.method());
        }
        registerRoute(cls, object, path, method);
      }
    } catch(Throwable throwable) {
      log.error("Register route exception", throwable);
    }
  }

  @Override
  public RouteInfo lookup(String url) {
    return routes.getOrDefault(url, null);
  }

  @Override
  public void registerRoute(String path, RequestHandler handler, HttpMethod httpMethod) {
    try {
      Class<? extends RequestHandler> ref = handler.getClass();
      Method handle = ref.getMethod("handle", RequestContext.class);
      this.registerRoute(RequestHandler.class, handler, path, handle, httpMethod);
    } catch(NoSuchMethodException e) {
      log.error("There is no such method {}", "handle", e);
    }
  }

  /**
   * add route
   *
   * @param cls    route class
   * @param url    @GET/@POST.. value
   * @param method Mapping annotation annotation method
   * @throws Throwable reflection exception
   */
  private void registerRoute(Class<?> cls, Object bean, String url, Method method) throws Throwable {
    Annotation[] annotations = method.getAnnotations();
    if (annotations.length != 0) {
      for (Annotation annotation : annotations) {
        String routeUrl = "/";
        Class<? extends Annotation> annotationType = annotation.annotationType();
        Path path = annotationType.getAnnotation(Path.class);
        RestPath restPath = annotationType.getAnnotation(RestPath.class);
        if (Objects.isNull(path) && Objects.isNull(restPath)) {
          continue;
        }
        HttpMethod httpMethod = path.method();
        Method valueMethod = annotationType.getMethod("value");
        Object valueInvokeResult = lookup.unreflect(valueMethod).bindTo(annotation).invoke();
        if (!Objects.isNull(valueInvokeResult) && !valueInvokeResult.equals(routeUrl)) {
          routeUrl = String.join(routeUrl, String.valueOf(valueInvokeResult));
        }
        String completeUrl = this.getMethodUrl(url, routeUrl);
        this.registerRoute(cls, bean, completeUrl, method, httpMethod);
      }
    }
  }

  /**
   * add route
   *
   * @param clazz      route class
   * @param method     Mapping annotation annotation method
   * @param httpMethod http method
   */
  private void registerRoute(Class<?> clazz, Object bean, String completeUrl, Method method, HttpMethod httpMethod) {
    if (completeUrl.trim().isEmpty()) {
      return;
    }
    RouteInfo routeInfo = createRoute(clazz, bean, method, httpMethod, completeUrl);
    if (this.routes.containsKey(completeUrl)) {
      if (log.isDebugEnabled()) {
        log.debug("Registered request route URL is duplicated :{}", completeUrl);
      }
      throw new RouteRepeatException("Registered request route URL is duplicated : " + completeUrl);
    } else {
      this.registerRoute(completeUrl, routeInfo);
    }
  }

  /**
   * Create route
   *
   * @param clazz       Route class
   * @param method      Route method
   * @param httpMethod  Route root path
   * @param completeUrl Complete route path
   * @return Route
   */
  private RouteInfo createRoute(Class<?> clazz, Object bean, Method method, HttpMethod httpMethod, String completeUrl) {
    RouteInfo routeInfo = RouteInfo.of(completeUrl, clazz, bean, method, httpMethod);

    boolean isAllJsonResponse = Objects.isNull(clazz.getAnnotation(RestPath.class));
    boolean isJsonResponse = Objects.isNull(method.getAnnotation(JSON.class));
    boolean isViewResponse = Objects.isNull(method.getAnnotation(View.class));

    if (isJsonResponse || (isAllJsonResponse && isViewResponse)) {
      routeInfo.setViewType(ViewType.JSON);
    }
    if (!isViewResponse) {
      routeInfo.setViewType(ViewType.HTML);
      routeInfo.setHtmlView(new PebbleHTMLView());
    }
    if (isAllJsonResponse && isJsonResponse && isViewResponse) {
      routeInfo.setViewType(ViewType.TEXT);
    }
    return routeInfo;
  }

  /**
   * add route
   *
   * @param url   url
   * @param routeInfo Route info
   */
  private void registerRoute(String url, RouteInfo routeInfo) {
    this.routes.put(url, routeInfo);
  }

  /**
   * Get the complete mapped address
   *
   * @param baseUrl          The address of @Path or @RestPath on the class
   * @param methodMappingUrl Annotated address on method
   * @return complete mapped address
   */
  protected String getMethodUrl(String baseUrl, String methodMappingUrl) {
    StringBuilder url = new StringBuilder(256);
    url.append((baseUrl == null || baseUrl.trim().isEmpty()) ? "" : baseUrl.trim());
    if (Objects.nonNull(methodMappingUrl) && !methodMappingUrl.trim().isEmpty()) {
      String methodMappingUrlTrim = methodMappingUrl.trim();
      if (!methodMappingUrlTrim.startsWith("/")) {
        methodMappingUrlTrim = "/" + methodMappingUrlTrim;
      }
      if (url.toString().endsWith("/")) {
        url.setLength(url.length() - 1);
      }
      url.append(methodMappingUrlTrim);
    }
    return url.toString();
  }
}
