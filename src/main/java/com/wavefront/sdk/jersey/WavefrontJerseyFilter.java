package com.wavefront.sdk.jersey;

import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.routing.RoutingContext;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import jersey.repackaged.com.google.common.base.Preconditions;

import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;
import static com.wavefront.sdk.common.Constants.WAVEFRONT_PROVIDED_SOURCE;
import static com.wavefront.sdk.jaxrs.Constants.PROPERTY_NAME;
import static com.wavefront.sdk.jaxrs.Constants.WF_SPAN_HEADER;
import static com.wavefront.sdk.jersey.Constants.JERSEY_SERVER_COMPONENT;
import static com.wavefront.sdk.jersey.MetricNameUtils.REQUEST_PREFIX;
import static com.wavefront.sdk.jersey.MetricNameUtils.RESPONSE_PREFIX;

/**
 * A filter to generate Wavefront metrics and histograms for Jersey API requests/responses.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyFilter implements ContainerRequestFilter, ContainerResponseFilter {
  private static final Logger logger = Logger.getLogger(
      WavefrontJerseyFilter.class.getName());
  private final SdkReporter wfJerseyReporter;
  private final ApplicationTags applicationTags;
  private final ThreadLocal<StatsContext> statsContextThreadLocal = new ThreadLocal<>();
  private final ConcurrentMap<MetricName, AtomicInteger> gauges = new ConcurrentHashMap<>();

  @Nullable
  private final Tracer tracer;

  private WavefrontJerseyFilter(SdkReporter wfJerseyReporter,
                                ApplicationTags applicationTags,
                                @Nullable Tracer tracer) {
    Preconditions.checkNotNull(wfJerseyReporter, "Invalid JerseyReporter");
    Preconditions.checkNotNull(applicationTags, "Invalid ApplicationTags");
    this.wfJerseyReporter = wfJerseyReporter;
    this.applicationTags = applicationTags;
    this.tracer = tracer;
  }

  public static final class Builder {

    private final SdkReporter wfJerseyReporter;
    private final ApplicationTags applicationTags;
    @Nullable
    private Tracer tracer;

    public Builder(SdkReporter wfJerseyReporter, ApplicationTags applicationTags) {
      this.wfJerseyReporter = wfJerseyReporter;
      this.applicationTags = applicationTags;
    }

    public Builder withTracer(Tracer tracer) {
      this.tracer = tracer;
      return this;
    }

    public WavefrontJerseyFilter build() {
      return new WavefrontJerseyFilter(wfJerseyReporter, applicationTags, tracer);
    }

  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    try {
      processRequest(containerRequestContext);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Exception filtering jersey containerRequest", e);
    }
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext,
                     ContainerResponseContext containerResponseContext) {
    try {
      processResponse(containerRequestContext, containerResponseContext);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Exception filtering jersey containerResponse", e);
    }
  }

  private void processRequest(ContainerRequestContext containerRequestContext) {
    if (containerRequestContext instanceof ContainerRequest) {
      ContainerRequest request = (ContainerRequest) containerRequestContext;
      long startTime = System.currentTimeMillis();
      long startTimeCpuNanos = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
      Optional<Pair<String, String>> pairOptional = MetricNameUtils.metricNameAndPath(request);
      if (!pairOptional.isPresent()) {
        statsContextThreadLocal.set(new StatsContext(startTime, startTimeCpuNanos, null, null));
        return;
      }
      String requestMetricKey = REQUEST_PREFIX + pairOptional.get()._1;
      String finalMatchingPath = pairOptional.get()._2;
      ExtendedUriInfo uriInfo = request.getUriInfo();
      Pair<String, String> pair = getClassAndMethodName(uriInfo);
      String finalClassName = pair._1;
      String finalMethodName = pair._2;

      if (tracer != null) {
        Tracer.SpanBuilder spanBuilder = tracer.buildSpan(finalClassName.substring(
            finalClassName.lastIndexOf('.') + 1) + "." + finalMethodName).
            withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER).
            withTag("jersey.resource.class", finalClassName).
            withTag("jersey.path", finalMatchingPath);
        SpanContext parentSpanContext = parentSpanContext(containerRequestContext);
        if (parentSpanContext != null) {
          spanBuilder.asChildOf(parentSpanContext);
        }
        Scope scope = spanBuilder.startActive(false);
        decorateRequest(containerRequestContext, scope.span());
        containerRequestContext.setProperty(PROPERTY_NAME, scope);
      }

      /* Gauges
       * 1) jersey.server.request.api.v2.alert.summary.GET.inflight
       * 2) jersey.server.total_requests.inflight
       */
      AtomicInteger apiInflight = getGaugeValue(new MetricName(requestMetricKey + ".inflight",
          getCompleteTagsMap(finalClassName, finalMethodName)));
      apiInflight.incrementAndGet();
      AtomicInteger totalInflight = getGaugeValue(new MetricName("total_requests.inflight",
          new HashMap<String, String>() {{
            put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
                applicationTags.getCluster());
            put(SERVICE_TAG_KEY, applicationTags.getService());
            put(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL :
                applicationTags.getShard());
          }}));
      totalInflight.incrementAndGet();
      statsContextThreadLocal.set(new StatsContext(startTime, startTimeCpuNanos, apiInflight,
          totalInflight));
    }
  }

  private void processResponse(ContainerRequestContext containerRequestContext,
                               ContainerResponseContext containerResponseContext) {
    if (tracer != null) {
      try {
        Scope scope = (Scope) containerRequestContext.getProperty(PROPERTY_NAME);
        if (scope != null) {
          decorateResponse(containerResponseContext, scope.span());
          scope.close();
          scope.span().finish();
        }
      } catch (ClassCastException ex) {
        // no valid scope found
      }
    }
    if (containerRequestContext instanceof ContainerRequest) {
      ContainerRequest request = (ContainerRequest) containerRequestContext;
      ExtendedUriInfo uriInfo = request.getUriInfo();

      Pair<String, String> pair = getClassAndMethodName(uriInfo);
      String finalClassName = pair._1;
      String finalMethodName = pair._2;

      Optional<Pair<String, String>> apiPathOptionalPair =
          MetricNameUtils.metricNameAndPath(request);
      if (!apiPathOptionalPair.isPresent()) {
        return;
      }
      if (tracer != null) {
        String matchingPath = apiPathOptionalPair.get()._2;
        containerResponseContext.getHeaders().add(WF_SPAN_HEADER, matchingPath);
      }

      String responseMetricKeyWithoutStatus = RESPONSE_PREFIX + apiPathOptionalPair.get()._1;
      String responseMetricKey =
          responseMetricKeyWithoutStatus + "." + containerResponseContext.getStatus();

      /* Gauges
       * 1) jersey.server.request.api.v2.alert.summary.GET.inflight
       * 2) jersey.server.total_requests.inflight
       */
      Map<String, String> completeTagsMap = getCompleteTagsMap(finalClassName, finalMethodName);

      // Response metrics and histograms below
      Map<String, String> aggregatedPerShardMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerSourceMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
      }};

      Map<String, String> overallAggregatedPerShardMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL :
            applicationTags.getShard());
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> aggregatedPerServiceMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerServiceMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put(SERVICE_TAG_KEY, applicationTags.getService());
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> aggregatedPerClusterMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
            applicationTags.getCluster());
        put("jersey.resource.class", finalClassName);
        put("jersey" + ".resource.method", finalMethodName);
        put("source", WAVEFRONT_PROVIDED_SOURCE);
      }};

      Map<String, String> overallAggregatedPerClusterMap = new HashMap<String, String>() {{
        put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
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
       * 6) jersey.server.response.api.v2.alert.summary.GET.errors (Counter)
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
       * 1) jersey.server.response.errors.aggregated_per_source (Counter)
       * 2) jersey.server.response.errors.aggregated_per_shard (DeltaCounter)
       * 3) jersey.server.response.errors.aggregated_per_service (DeltaCounter)
       * 4) jersey.server.response.errors.aggregated_per_cluster (DeltaCounter)
       * 5) jersey.server.response.errors.aggregated_per_application (DeltaCounter)
       */
      if (isErrorStatusCode(containerResponseContext)) {
        wfJerseyReporter.incrementCounter(new MetricName(responseMetricKeyWithoutStatus + ".errors",
            completeTagsMap));
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

      StatsContext statsContext = statsContextThreadLocal.get();
      if (statsContext != null) {
        // update api inflight and total inflight gauges.
        if (statsContext.getApiInflight() != null) {
          statsContext.getApiInflight().decrementAndGet();
        }

        if (statsContext.getTotalInflight() != null) {
          statsContext.getTotalInflight().decrementAndGet();
        }

        /*
         * WavefrontHistograms
         * 1) jersey.server.response.api.v2.alert.summary.GET.200.latency
         * 2) jersey.server.response.api.v2.alert.summary.GET.200.cpu_ns
         */
        long cpuNanos = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() -
            statsContext.getStartCpuNanos();
        wfJerseyReporter.updateHistogram(new MetricName(responseMetricKey + ".cpu_ns",
            completeTagsMap), cpuNanos);

        long apiLatency = System.currentTimeMillis() - statsContext.getStartTime();
        wfJerseyReporter.updateHistogram(new MetricName(responseMetricKey + ".latency",
            completeTagsMap), apiLatency);
        /*
         * total time spent counter: jersey.server.response.api.v2.alert.summary.GET.200.total_time
         */
        wfJerseyReporter.incrementCounter(new MetricName(responseMetricKey + ".total_time",
            completeTagsMap), apiLatency);
      }
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
      put(CLUSTER_TAG_KEY, applicationTags.getCluster() == null ? NULL_TAG_VAL :
          applicationTags.getCluster());
      put(SERVICE_TAG_KEY, applicationTags.getService());
      put(SHARD_TAG_KEY,
          applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
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

  private void decorateRequest(ContainerRequestContext requestContext, Span span) {
    Tags.COMPONENT.set(span, JERSEY_SERVER_COMPONENT);
    Tags.HTTP_METHOD.set(span, requestContext.getMethod());
    String urlStr = null;
    URL url;
    try {
      url = requestContext.getUriInfo().getRequestUri().toURL();
      urlStr = url.toString();
    } catch (MalformedURLException e) {
      // ignoring returning null
    }
    if (urlStr != null) {
      Tags.HTTP_URL.set(span, urlStr);
    }
  }

  private void decorateResponse(ContainerResponseContext responseContext, Span span) {
    Tags.HTTP_STATUS.set(span, responseContext.getStatus());
    if (isErrorStatusCode(responseContext)) {
      Tags.ERROR.set(span, true);
    }
  }

  private boolean isErrorStatusCode(ContainerResponseContext containerResponseContext) {
    int statusCode = containerResponseContext.getStatus();
    return statusCode >= 400 && statusCode <= 599;
  }

  public class ServerHeadersExtractTextMap implements TextMap {

    private final MultivaluedMap<String, String> headers;

    ServerHeadersExtractTextMap(MultivaluedMap<String, String> headers) {
      this.headers = headers;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      return new MultivaluedMapFlatIterator<>(headers.entrySet());
    }

    @Override
    public void put(String key, String value) {
      throw new UnsupportedOperationException(ServerHeadersExtractTextMap.class.getName() +
          " should only be used with Tracer.extract()");
    }
  }

  public Tracer getTracer() {
    return this.tracer;
  }

  public static final class MultivaluedMapFlatIterator<K, V> implements Iterator<Map.Entry<K, V>> {
    private final Iterator<Map.Entry<K, List<V>>> mapIterator;
    private Map.Entry<K, List<V>> mapEntry;
    private Iterator listIterator;

    MultivaluedMapFlatIterator(Set<Map.Entry<K, List<V>>> multiValuesEntrySet) {
      this.mapIterator = multiValuesEntrySet.iterator();
    }

    public boolean hasNext() {
      return this.listIterator != null && this.listIterator.hasNext() || this.mapIterator.hasNext();
    }

    public Map.Entry<K, V> next() {
      if (this.mapEntry == null || !this.listIterator.hasNext() && this.mapIterator.hasNext()) {
        this.mapEntry = this.mapIterator.next();
        this.listIterator = ((List)this.mapEntry.getValue()).iterator();
      }

      return this.listIterator.hasNext() ? new AbstractMap.SimpleImmutableEntry(
          this.mapEntry.getKey(), this.listIterator.next()) : new AbstractMap.SimpleImmutableEntry(
              this.mapEntry.getKey(), (Object)null);
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class StatsContext {
    private final long startTime;
    private final long startCpuNanos;
    @Nullable
    private final AtomicInteger apiInflight;
    @Nullable
    private final AtomicInteger totalInflight;

    StatsContext(long startTime, long startCpuNanos, AtomicInteger apiInflight,
                 AtomicInteger totalInflight) {
      this.startTime = startTime;
      this.startCpuNanos = startCpuNanos;
      this.apiInflight = apiInflight;
      this.totalInflight = totalInflight;
    }

    public long getStartTime() {
      return startTime;
    }

    public long getStartCpuNanos() {
      return startCpuNanos;
    }

    public AtomicInteger getApiInflight() {
      return apiInflight;
    }

    public AtomicInteger getTotalInflight() {
      return totalInflight;
    }
  }
}
