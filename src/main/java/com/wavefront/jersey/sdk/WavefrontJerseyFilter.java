package com.wavefront.jersey.sdk;

import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.jersey.sdk.reporter.JerseyReporter;
import com.wavefront.sdk.common.Pair;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.internal.routing.RoutingContext;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

import static com.wavefront.jersey.sdk.Constants.NULL_TAG_VAL;

/**
 * A filter to generate Wavefront metrics and histograms for Jersey API requests/responses.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyFilter implements ContainerRequestFilter, ContainerResponseFilter {

  static final String WAVEFRONT_PROVIDED_SOURCE = "wavefront-provided";

  private final JerseyReporter wfJerseyReporter;
  private final ApplicationTags applicationTags;
  private final ThreadLocal<Long> startTime = new ThreadLocal<>();
  private final ThreadLocal<Long> startTimeCpuNanos = new ThreadLocal<>();
  private final ConcurrentMap<MetricName, AtomicInteger> gauges = new ConcurrentHashMap<>();

  public WavefrontJerseyFilter(JerseyReporter wfJerseyReporter, ApplicationTags applicationTags) {
    this.wfJerseyReporter = wfJerseyReporter;
    this.applicationTags = applicationTags;
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
}
