package com.wavefront.jersey.sdk.reporter;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.jersey.sdk.ApplicationTags;
import com.wavefront.sdk.common.WavefrontSender;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * Wavefront reporter for your Jersey based application responsible for reporting metrics and
 * histograms out of the box for you.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyReporter implements JerseyReporter {

  private final WavefrontInternalReporter wfReporter;

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
  }

  public static class Builder {
    // Required parameters
    private final ApplicationTags applicationTags;
    private final String prefix = "jersey.server";

    // Optional parameters
    private int reportingIntervalSeconds = 60;

    @Nullable
    private String source;

    /**
     * Builder to build WavefrontJerseyReporter.
     *
     * @param applicationTags  metadata about your application that you want to be propagated as
     *                         tags when metrics/histograms are sent to Wavefront.
     */
    public Builder(ApplicationTags applicationTags) {
      this.applicationTags = applicationTags;
    }

    /**
     * Set reporting interval i.e. how often you want to report the metrics/histograms to
     * Wavefront.
     *
     * @param reportingIntervalSeconds reporting interval in seconds.
     * @return {@code this}.
     */
    public Builder reportingIntervalSeconds(int reportingIntervalSeconds) {
      this.reportingIntervalSeconds = reportingIntervalSeconds;
      return this;
    }

    /**
     * Set the source tag for your metric and histograms.
     *
     * @param source Name of the source/host where your application is running.
     * @return {@code this}.
     */
    public Builder source(String source) {
      this.source = source;
      return this;
    }

    /**
     * Build WavefrontJerseyReporter.
     *
     * @param wavefrontSender send data to Wavefront via proxy or direct ingestion.
     * @return An instance of {@link WavefrontJerseyReporter}.
     */
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
      pointTags.put("application", applicationTags.getApplication());
      pointTags.putAll(applicationTags.getCustomTags());

      WavefrontInternalReporter wfReporter = new WavefrontInternalReporter.Builder().
          prefixedWith(prefix).withSource(source).withReporterPointTags(pointTags).
          reportMinuteDistribution().build(wavefrontSender);
      wfReporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
      return new WavefrontJerseyReporter(wfReporter);
    }
  }
}
