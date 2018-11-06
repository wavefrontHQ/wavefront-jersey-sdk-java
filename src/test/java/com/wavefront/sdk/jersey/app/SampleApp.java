package com.wavefront.sdk.jersey.app;

import com.google.common.collect.Lists;
import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.opentracing.WavefrontSpan;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.jersey.WavefrontJerseyFilter;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.ServerConnector;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SampleApp extends Application<Configuration> {
  private static final String APPLICATION = "wavefront";
  public static final String CLUSTER = "prod";
  public static final String SERVICE = "alerting";
  public static final String SHARD = "secondary";
  private int httpPort;

  private final ConcurrentMap<MetricName, AtomicInteger> cache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, WavefrontSpan> spanCache = new ConcurrentHashMap<>();

  private AtomicInteger computeIfAbsent(MetricName metricName) {
    return cache.computeIfAbsent(metricName, key -> new AtomicInteger());
  }

  @Override
  public void run(Configuration configuration, Environment environment) {
    DefaultServerFactory defaultHttp = (DefaultServerFactory) configuration.getServerFactory();
    HttpConnectorFactory appConnectorFactory = new HttpConnectorFactory();
    HttpConnectorFactory adminConnectorFactory = new HttpConnectorFactory();
    // set random available port.
    appConnectorFactory.setPort(0);
    adminConnectorFactory.setPort(0);
    defaultHttp.setApplicationConnectors(Lists.newArrayList(appConnectorFactory));
    defaultHttp.setAdminConnectors(Lists.newArrayList(adminConnectorFactory));
    environment.lifecycle().addServerLifecycleListener(server -> {
      httpPort = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    });
    environment.jersey().register(new SampleResource());
    environment.getApplicationContext().setContextPath("/sample");
    environment.jersey().register(new WavefrontJerseyFilter.Builder(new SdkReporter() {
      @Override
      public void incrementCounter(MetricName metricName) {
        computeIfAbsent(metricName).incrementAndGet();
      }

      @Override
      public void incrementDeltaCounter(MetricName metricName) {
        computeIfAbsent(metricName).incrementAndGet();
      }

      @Override
      public void updateHistogram(MetricName metricName, long latencyMillis) {
        computeIfAbsent(metricName).incrementAndGet();
      }

      @Override
      public void registerGauge(MetricName metricName, AtomicInteger value) {
        computeIfAbsent(metricName);
      }

      @Override
      public void start() {
        // no-op
      }

      @Override
      public void stop() {
        // no-op
      }
    }, new ApplicationTags.Builder(APPLICATION, SERVICE).cluster(CLUSTER).shard(SHARD).
        customTags(new HashMap<String, String>() {{
          put("location", "SF");
          put("env", "Staging");
        }}).build()).withTracer(new WavefrontTracer.Builder(new Reporter() {
      @Override
      public void report(WavefrontSpan span) {
        spanCache.putIfAbsent(span.getOperationName(), span);
      }

      @Override
      public int getFailureCount() {
        return 0;
      }

      @Override
      public void close() {
        // no-op
      }
    }, new ApplicationTags.Builder("myApp", "myService").build()).build()).build());
  }

  public int reportedValue(MetricName metricName) {
    return computeIfAbsent(metricName).get();
  }

  public WavefrontSpan reportedSpan(String operationName) {
    return spanCache.get(operationName);
  }

  @Path("/sample/foo")
  @Produces(MediaType.TEXT_PLAIN)
  public class SampleResource {

    // CRUD operations

    // C => create
    @POST
    @Path("/bar")
    public void barCreate() {
      // no-op
    }

    // R => read
    @GET
    @Path("/bar/{id}")
    public String barGet() {
      return "don't care";
    }

    // R => getAll
    @GET
    @Path("/bar")
    public String getAll() {
      return "don't care";
    }

    // U => update
    @PUT
    @Path("/bar/{id}")
    public void barUpdate() {
      // no-op
    }

    // D => delete
    @DELETE
    @Path("/bar/{id}")
    public void barDelete() {
      // no-op
    }
  }

  public int getHttpPort() {
    return httpPort;
  }
}
