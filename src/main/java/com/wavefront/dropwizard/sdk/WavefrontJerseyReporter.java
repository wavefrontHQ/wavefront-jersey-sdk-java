package com.wavefront.dropwizard.sdk;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.WavefrontSender;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wavefront reporter for your Dropwizard Application.
 * Will report metrics and histograms out of the box for you.
 * This is a uber reporter class for your Dropwizard application that leverages WavefrontReporter
 * under the hood to report metrics and histograms from your Dropwizard app to Wavefront
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyReporter implements JerseyReporter {

  private static final String METRIC_PREFIX = "dropwizard.api";

  private final WavefrontInternalReporter wfReporter;

  public static class Builder {
    /**
     * How often do you want to report the metrics/histograms to Wavefront
     */
    private int reportingIntervalSeconds = 60;

    /**
     * Name of the application (e.g. "OrderingApplication").
     * This is a required field (defaults to "defaultApplication")
     */
    private String application = "defaultApplication";

    /**
     * Name of the service (e.g. "InventoryService") for the ordering app.
     * This is a required field (defaults to "defaultService")
     */
    private String service = "defaultService";


    @Nullable
    private String source;

    public Builder reportingIntervalSeconds(int reportingIntervalSeconds) {
      this.reportingIntervalSeconds = reportingIntervalSeconds;
      return this;
    }

    public Builder application(String application) {
      this.application = application;
      return this;
    }

    public Builder source(String source) {
      this.source = source;
      return this;
    }

    public WavefrontJerseyReporter build(WavefrontSender wavefrontSender) {
      if (source == null) {
        try {
          source = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
          // Should never happen
          source = "unknown";
        }
      }

      Map<String, String> pointTags = new HashMap<>();
      pointTags.put("application", application);

      WavefrontInternalReporter wfReporter = new WavefrontInternalReporter.Builder().prefixedWith(METRIC_PREFIX).
              withSource(source).withReporterPointTags(pointTags).reportMinuteDistribution().build(wavefrontSender);
      wfReporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
      return new WavefrontJerseyReporter(wfReporter);
    }
  }

  private WavefrontJerseyReporter(WavefrontInternalReporter wfReporter) {
    this.wfReporter = wfReporter;
  }

  @Override
  public void incrementCounter(MetricName metricName) {
    wfReporter.newCounter(metricName).inc();
  }

  @Override
  public void incrementDeltaCounter(MetricName metricName) {
    wfReporter.newDeltaCounter(metricName).inc();
  }

  @Override
  public void registerGauge(MetricName metricName, AtomicInteger value) {
    wfReporter.newGauge(metricName, () -> (double) value.get());
  }

  @Override
  public void updateHistogram(MetricName metricName, long latencyMillis) {
    wfReporter.newWavefrontHistogram(metricName).update(latencyMillis);
    /*
     * Since WavefrontHistogram might not always be enabled on Wavefront cluster, for now,
     * Report dropwizard histogram along with WavefrontHistogram
     * Also, change the name as Dropwizard does not allow 2 histograms with the same name inside
     * the same metric registry
     */
    wfReporter.newHistogram(new MetricName(metricName.getKey() + ".histogram",
        metricName.getTags())).update(latencyMillis);
  }
}
