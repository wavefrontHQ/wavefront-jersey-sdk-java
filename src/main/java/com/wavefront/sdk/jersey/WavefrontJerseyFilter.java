package com.wavefront.sdk.jersey;

import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.contrib.jaxrs2.internal.CastUtils;
import io.opentracing.contrib.jaxrs2.server.ServerHeadersExtractTextMap;
import io.opentracing.contrib.jaxrs2.server.ServerSpanDecorator;
import io.opentracing.tag.Tags;
import io.opentracing.propagation.Format;
import io.opentracing.contrib.jaxrs2.internal.SpanWrapper;
import io.opentracing.contrib.jaxrs2.server.OperationNameProvider;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.routing.RoutingContext;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import jersey.repackaged.com.google.common.base.Preconditions;

import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.WAVEFRONT_PROVIDED_SOURCE;
import static io.opentracing.contrib.jaxrs2.internal.SpanWrapper.PROPERTY_NAME;

/**
 * A filter to generate Wavefront metrics and histograms for Jersey API requests/responses.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyFilter implements ContainerRequestFilter, ContainerResponseFilter, Filter {

  private final SdkReporter wfJerseyReporter;
  private final ApplicationTags applicationTags;
  private final ThreadLocal<Long> startTime = new ThreadLocal<>();
  private final ThreadLocal<Long> startTimeCpuNanos = new ThreadLocal<>();
  private final ConcurrentMap<MetricName, AtomicInteger> gauges = new ConcurrentHashMap<>();

  private Tracer tracer;
  private List<ServerSpanDecorator> spanDecorators;
  private OperationNameProvider.Builder operationNameProvider;

  public WavefrontJerseyFilter(SdkReporter wfJerseyReporter, ApplicationTags applicationTags) {
    Preconditions.checkNotNull(wfJerseyReporter, "Invalid JerseyReporter");
    Preconditions.checkNotNull(applicationTags, "Invalid ApplicationTags");
    this.wfJerseyReporter = wfJerseyReporter;
    this.applicationTags = applicationTags;
  }

  public WavefrontJerseyFilter(SdkReporter wfJerseyReporter, ApplicationTags applicationTags, Tracer tracer) {
    this(wfJerseyReporter, applicationTags);
    this.tracer = tracer;
    this.spanDecorators = Collections.singletonList(ServerSpanDecorator.STANDARD_TAGS);
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) throws IOException {
    if (containerRequestContext instanceof ContainerRequest) {
      ContainerRequest request = (ContainerRequest) containerRequestContext;
      startTime.set(System.currentTimeMillis());
      startTimeCpuNanos.set(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime());
      Optional<String> optional = MetricNameUtils.metricName(request);
      if (!optional.isPresent()) {
        return;
      }
      String requestMetricKey = optional.get();
      ExtendedUriInfo uriInfo = request.getUriInfo();
      Pair<String, String> pair = getClassAndMethodName(uriInfo);
      String finalClassName = pair._1;
      String finalMethodName = pair._2;

      if (tracer != null) {
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(finalMethodName)
                .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        SpanContext parentSpanContext = parentSpanContext(containerRequestContext);
        if (parentSpanContext != null) {
          spanBuilder.asChildOf(parentSpanContext);
        }
        Scope scope = spanBuilder.startActive(false);
        if (spanDecorators != null) {
          for (ServerSpanDecorator decorator: spanDecorators) {
            decorator.decorateRequest(containerRequestContext, scope.span());
          }
        }
        containerRequestContext.setProperty(SpanWrapper.PROPERTY_NAME, new SpanWrapper(scope));
      }

       /* Gauges
       * 1) jersey.server.request.api.v2.alert.summary.GET.inflight
       * 2) jersey.server.total_requests.inflight
       */
       getGaugeValue(new MetricName(requestMetricKey + ".inflight",
          getCompleteTagsMap(finalClassName, finalMethodName))).incrementAndGet();
       getGaugeValue(new MetricName("total_requests.inflight",
          new HashMap<String, String>() {{
            put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
                applicationTags.getCluster());
            put("service", applicationTags.getService());
            put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL :
                applicationTags.getShard());
          }})).incrementAndGet();
    }
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext,
                     ContainerResponseContext containerResponseContext)
      throws IOException {
    SpanWrapper spanWrapper = CastUtils.cast(containerRequestContext.getProperty(PROPERTY_NAME), SpanWrapper.class);
    if (spanWrapper == null) {
      return;
    }
    if (spanDecorators != null) {
      for (ServerSpanDecorator decorator: spanDecorators) {
        decorator.decorateResponse(containerResponseContext, spanWrapper.get());
      }
    }
    if (containerRequestContext instanceof ContainerRequest) {
      ContainerRequest request = (ContainerRequest) containerRequestContext;
      ExtendedUriInfo uriInfo = request.getUriInfo();

      Pair<String, String> pair = getClassAndMethodName(uriInfo);
      String finalClassName = pair._1;
      String finalMethodName = pair._2;

      Optional<String> optional = MetricNameUtils.metricName(request);
      if (!optional.isPresent()) {
        return;
      }
      String requestMetricKey = optional.get();
      optional = MetricNameUtils.metricName(request, containerResponseContext);
      if (!optional.isPresent()) {
        return;
      }
      String responseMetricKey = optional.get();

      /* Gauges
       * 1) jersey.server.request.api.v2.alert.summary.GET.inflight
       * 2) jersey.server.total_requests.inflight
       */
      Map<String, String> completeTagsMap = getCompleteTagsMap(finalClassName, finalMethodName);

      /*
       * Okay to do map.get(key) as the key will definitely be present in the map during the
       * response phase.
       */
      gauges.get(new MetricName(requestMetricKey + ".inflight", completeTagsMap)).
          decrementAndGet();
      gauges.get(new MetricName("total_requests.inflight",
          new HashMap<String, String>() {{
            put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
                applicationTags.getCluster());
            put("service", applicationTags.getService());
            put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL :
                applicationTags.getShard());
          }})).decrementAndGet();

      // Response metrics and histograms below
      Map<String, String> aggregatedPerShardMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("service", applicationTags.getService());
        put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerSourceMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("service", applicationTags.getService());
        put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
      }};

      Map<String, String> overallAggregatedPerShardMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("service", applicationTags.getService());
        put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> aggregatedPerServiceMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("service", applicationTags.getService());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerServiceMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("service", applicationTags.getService());
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> aggregatedPerClusterMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerClusterMap = new HashMap<String, String>() {{
        put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> aggregatedPerApplicationMap = new HashMap<String, String>() {{
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerApplicationMap = new HashMap<String, String>() {{
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      /*
       * Granular response metrics
       * 1) jersey.server.response.api.v2.alert.summary.GET.200.cumulative.count (Counter)
       * 2) jersey.server.response.api.v2.alert.summary.GET.200.aggregated_per_shard.count (DeltaCounter)
       * 3) jersey.server.response.api.v2.alert.summary.GET.200.aggregated_per_service.count (DeltaCounter)
       * 4) jersey.server.response.api.v2.alert.summary.GET.200.aggregated_per_cluster.count (DeltaCounter)
       * 5) jersey.server.response.api.v2.alert.summary.GET.200.aggregated_per_application.count (DeltaCounter)
       */
      wfJerseyReporter.incrementCounter(new MetricName(responseMetricKey +
          ".cumulative", completeTagsMap));
      if (applicationTags.getShard() != null) {
        wfJerseyReporter.incrementDeltaCounter(new MetricName(responseMetricKey +
            ".aggregated_per_shard", aggregatedPerShardMap));
      }
      wfJerseyReporter.incrementDeltaCounter(new MetricName(responseMetricKey +
          ".aggregated_per_service", aggregatedPerServiceMap));
      if (applicationTags.getCluster() != null) {
        wfJerseyReporter.incrementDeltaCounter(new MetricName(responseMetricKey +
            ".aggregated_per_cluster", aggregatedPerClusterMap));
      }
      wfJerseyReporter.incrementDeltaCounter(new MetricName(responseMetricKey +
          ".aggregated_per_application", aggregatedPerApplicationMap));

     /*
       * Overall error response metrics
       * 1) <prefix>.response.errors.aggregated_per_source (Counter)
       * 2) <prefix>.response.errors.aggregated_per_shard (DeltaCounter)
       * 3) <prefix>.response.errors.aggregated_per_service (DeltaCounter)
       * 4) <prefix>.response.errors.aggregated_per_cluster (DeltaCounter)
       * 5) <prefix>.response.errors.aggregated_per_application (DeltaCounter)
       */
      int statusCode = containerResponseContext.getStatus();
      if (statusCode >= 400 && statusCode <= 599) {
        wfJerseyReporter.incrementCounter(new MetricName("response.errors",
            completeTagsMap));
        wfJerseyReporter.incrementCounter(new MetricName(
            "response.errors.aggregated_per_source", overallAggregatedPerSourceMap));
        if (applicationTags.getShard() != null) {
          wfJerseyReporter.incrementDeltaCounter(new MetricName(
              "response.errors.aggregated_per_shard", overallAggregatedPerShardMap));
        }
        wfJerseyReporter.incrementDeltaCounter(new MetricName(
            "response.errors.aggregated_per_service", overallAggregatedPerServiceMap));
        if (applicationTags.getCluster() != null) {
          wfJerseyReporter.incrementDeltaCounter(new MetricName(
              "response.errors.aggregated_per_cluster", overallAggregatedPerClusterMap));
        }
        wfJerseyReporter.incrementDeltaCounter(new MetricName(
            "response.errors.aggregated_per_application", overallAggregatedPerApplicationMap));
      }

      /*
       * Overall response metrics
       * 1) jersey.server.response.completed.aggregated_per_source.count (Counter)
       * 2) jersey.server.response.completed.aggregated_per_shard.count (DeltaCounter)
       * 3) jersey.server.response.completed.aggregated_per_service.count (DeltaCounter)
       * 3) jersey.server.response.completed.aggregated_per_cluster.count (DeltaCounter)
       * 5) jersey.server.response.completed.aggregated_per_application.count (DeltaCounter)
       */
      wfJerseyReporter.incrementCounter(new MetricName("response.completed.aggregated_per_source",
          overallAggregatedPerSourceMap));
      if (applicationTags.getShard() != null) {
        wfJerseyReporter.incrementDeltaCounter(new MetricName("response" +
            ".completed.aggregated_per_shard", overallAggregatedPerShardMap));
      }
      wfJerseyReporter.incrementDeltaCounter(new MetricName("response" +
          ".completed.aggregated_per_service", overallAggregatedPerServiceMap));
      if (applicationTags.getCluster() != null) {
        wfJerseyReporter.incrementDeltaCounter(new MetricName("response" +
            ".completed.aggregated_per_cluster", overallAggregatedPerClusterMap));
      }
      wfJerseyReporter.incrementDeltaCounter(new MetricName("response" +
          ".completed.aggregated_per_application", overallAggregatedPerApplicationMap));

      /*
       * WavefrontHistograms
       * 1) jersey.server.response.api.v2.alert.summary.GET.200.latency
       * 2) jersey.server.response.api.v2.alert.summary.GET.200.cpu_ns
       */
      long cpuNanos = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() -
          startTimeCpuNanos.get();
      wfJerseyReporter.updateHistogram(new MetricName(responseMetricKey + ".cpu_ns",
          completeTagsMap), cpuNanos);

      long apiLatency = System.currentTimeMillis() - startTime.get();
      wfJerseyReporter.updateHistogram(new MetricName(responseMetricKey + ".latency",
              completeTagsMap), apiLatency);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletResponse httpResponse = (HttpServletResponse)response;
    HttpServletRequest httpRequest = (HttpServletRequest)request;

    try {
      chain.doFilter(request, response);
    } catch (Exception ex) {
      SpanWrapper spanWrapper = getSpanWrapper(httpRequest);
      if (spanWrapper != null) {
        Tags.HTTP_STATUS.set(spanWrapper.get(), httpResponse.getStatus());
        addExceptionLogs(spanWrapper.get(), ex);
      }
      throw ex;
    } finally {
      SpanWrapper spanWrapper = getSpanWrapper(httpRequest);
      if (spanWrapper != null) {
        spanWrapper.getScope().close();
        if (request.isAsyncStarted()) {
          request.getAsyncContext().addListener(new SpanFinisher(spanWrapper), request, response);
        } else {
          spanWrapper.finish();
        }
      }
    }
  }

  @Override
  public void destroy() {

  }

  private SpanWrapper getSpanWrapper(HttpServletRequest request) {
    return CastUtils.cast(request.getAttribute(SpanWrapper.PROPERTY_NAME), SpanWrapper.class);
  }

  static class SpanFinisher implements AsyncListener {
    private SpanWrapper spanWrapper;
    SpanFinisher(SpanWrapper spanWrapper) {
      this.spanWrapper = spanWrapper;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
      HttpServletResponse httpResponse = (HttpServletResponse)event.getSuppliedResponse();
      if (httpResponse.getStatus() >= 500) {
        addExceptionLogs(spanWrapper.get(), event.getThrowable());
      }
      Tags.HTTP_STATUS.set(spanWrapper.get(), httpResponse.getStatus());
      spanWrapper.finish();
    }
    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
    }
    @Override
    public void onError(AsyncEvent event) throws IOException {
      // this handler is called when exception is thrown in async handler
      // note that exception logs are added in filter not here
    }
    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
    }
  }

  private static void addExceptionLogs(Span span, Throwable throwable) {
    Tags.ERROR.set(span, true);
    if (throwable != null) {
      Map<String, Object> errorLogs = new HashMap<>(2);
      errorLogs.put("event", Tags.ERROR.getKey());
      errorLogs.put("error.object", throwable);
      span.log(errorLogs);
    }
  }

  private Pair<String, String> getClassAndMethodName(ExtendedUriInfo uriInfo) {
    String className = "unknown";
    String methodName = "unknown";

    if (uriInfo != null) {
      Class clazz = ((RoutingContext) uriInfo).getResourceClass();
      if (clazz != null) {
        className = clazz.getCanonicalName();
      }
      Method method = ((RoutingContext) uriInfo).getResourceMethod();
      if (method != null) {
        methodName = method.getName();
      }
    }
    return Pair.of(className, methodName);
  }

  private AtomicInteger getGaugeValue(MetricName metricName) {
    return gauges.computeIfAbsent(metricName, key -> {
      final AtomicInteger toReturn = new AtomicInteger();
      wfJerseyReporter.registerGauge(key, toReturn);
      return toReturn;
    });
  }

  private Map<String, String> getCompleteTagsMap(String finalClassName, String finalMethodName) {
    return new HashMap<String, String>() {{
      put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
          applicationTags.getCluster());
      put("service", applicationTags.getService());
      put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
      put("jersey.resource.class", finalClassName);
      put("jersey" + ".resource.method", finalMethodName);
    }};
  }

  private SpanContext parentSpanContext(ContainerRequestContext requestContext) {
    Span activeSpan = tracer.activeSpan();
    if (activeSpan != null) {
      return activeSpan.context();
    } else {
      return tracer.extract(
              Format.Builtin.HTTP_HEADERS,
              new ServerHeadersExtractTextMap(requestContext.getHeaders())
      );
    }
  }

  static class CustomeOperationNameProvider implements OperationNameProvider {
    private final String methodName;

    private CustomeOperationNameProvider(String methodName) {
      this.methodName = methodName;
    }

    static class Builder implements OperationNameProvider.Builder {

      @Override
      public OperationNameProvider build(Class<?> clazz, Method method) {
        return new CustomeOperationNameProvider(method.getName());
      }
    }

    @Override
    public String operationName(ContainerRequestContext requestContext) {
      return methodName;
    }
  }
}
