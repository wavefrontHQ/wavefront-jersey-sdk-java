package com.wavefront.sdk.jersey.reporter;

import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.HeartbeaterService;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import jersey.repackaged.com.google.common.base.Preconditions;

import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SDK_METRIC_PREFIX;
import static com.wavefront.sdk.jersey.Constants.JERSEY_SERVER_COMPONENT;

/**
 * Wavefront reporter for your Jersey based application responsible for reporting metrics and
 * histograms out of the box for you.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyReporter implements SdkReporter {

  private final WavefrontInternalReporter wfReporter;
  private final WavefrontInternalReporter sdkMetricsReporter;
  private final int reportingIntervalSeconds;
  private final HeartbeaterService heartbeaterService;

  @Deprecated
  private WavefrontJerseyReporter(WavefrontInternalReporter wfReporter,
                                  int reportingIntervalSeconds,
                                  WavefrontMetricSender wavefrontMetricSender,
                                  ApplicationTags applicationTags,
                                  String source) {
    this(wfReporter, reportingIntervalSeconds, wavefrontMetricSender, applicationTags, source,
        null);
  }

  private WavefrontJerseyReporter(WavefrontInternalReporter wfReporter,
                                  int reportingIntervalSeconds,
                                  WavefrontMetricSender wavefrontMetricSender,
                                  ApplicationTags applicationTags,
                                  String source,
                                  WavefrontInternalReporter sdkMetricsReporter) {
    Preconditions.checkNotNull(wfReporter, "Invalid wfReporter");
    Preconditions.checkNotNull(wavefrontMetricSender, "Invalid wavefrontSender");
    Preconditions.checkNotNull(applicationTags, "Invalid ApplicationTags");
    this.wfReporter = wfReporter;
    this.reportingIntervalSeconds = reportingIntervalSeconds;
    this.sdkMetricsReporter = sdkMetricsReporter;
    heartbeaterService = new HeartbeaterService(wavefrontMetricSender, applicationTags,
        Collections.singletonList(JERSEY_SERVER_COMPONENT), source);
  }

  @Override
  public void incrementCounter(MetricName metricName) {
    wfReporter.newCounter(metricName).inc();
  }

  @Override
  public void incrementCounter(MetricName metricName, long n) {
    wfReporter.newCounter(metricName).inc(n);
  }

  @Override
  public void incrementDeltaCounter(MetricName metricName) {
    wfReporter.newDeltaCounter(metricName).inc();
  }

  @Override
  public void registerGauge(MetricName metricName, AtomicInteger value) {
    wfReporter.newGauge(metricName, () -> (() -> (double) value.get()));
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
    public Builder withSource(String source) {
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
      pointTags.put(APPLICATION_TAG_KEY, applicationTags.getApplication());
      if (applicationTags.getCustomTags() != null) {
        pointTags.putAll(applicationTags.getCustomTags());
      }

      WavefrontInternalReporter wfReporter = new WavefrontInternalReporter.Builder().
          prefixedWith(prefix).withSource(source).withReporterPointTags(pointTags).
          reportMinuteDistribution().build(wavefrontSender);

      WavefrontInternalReporter sdkMetricsReporter = new WavefrontInternalReporter.Builder().
          prefixedWith(SDK_METRIC_PREFIX + ".jersey").withSource(source).
          withReporterPointTags(pointTags).build(wavefrontSender);
      double sdkVersion = Utils.getSemVer();
      sdkMetricsReporter.newGauge(new MetricName("version", Collections.emptyMap()),
          () -> (() -> sdkVersion));

      return new WavefrontJerseyReporter(wfReporter, reportingIntervalSeconds, wavefrontSender,
              applicationTags, source, sdkMetricsReporter);
    }
  }

  @Override
  public void start() {
    wfReporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
    if (sdkMetricsReporter != null) {
      sdkMetricsReporter.start(1, TimeUnit.MINUTES);
    }
  }

  @Override
  public void stop() {
    heartbeaterService.close();
    wfReporter.stop();
    if (sdkMetricsReporter != null) {
      sdkMetricsReporter.stop();
    }
  }
}
