package com.wavefront.sdk.jersey.reporter;

import com.wavefront.internal.reporter.SdkReporter;
import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.HeartbeaterService;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jersey.repackaged.com.google.common.base.Preconditions;

import static com.wavefront.sdk.jersey.Constants.JERSEY_SERVER_COMPONENT;

/**
 * Wavefront reporter for your Jersey based application responsible for reporting metrics and
 * histograms out of the box for you.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyReporter implements SdkReporter {

  private final WavefrontInternalReporter wfReporter;
  private final int reportingIntervalSeconds;
  private final HeartbeaterService heartbeaterService;

  private WavefrontJerseyReporter(WavefrontInternalReporter wfReporter,
                                  int reportingIntervalSeconds,
                                  WavefrontMetricSender wavefrontMetricSender,
                                  ApplicationTags applicationTags,
                                  String source) {
    Preconditions.checkNotNull(wfReporter, "Invalid wfReporter");
    Preconditions.checkNotNull(wavefrontMetricSender, "Invalid wavefrontSender");
    Preconditions.checkNotNull(applicationTags, "Invalid ApplicationTags");
    this.wfReporter = wfReporter;
    this.reportingIntervalSeconds = reportingIntervalSeconds;
    heartbeaterService = new HeartbeaterService(wavefrontMetricSender, applicationTags,
            JERSEY_SERVER_COMPONENT, source);
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
      pointTags.put("application", applicationTags.getApplication());
      if (applicationTags.getCustomTags() != null) {
        pointTags.putAll(applicationTags.getCustomTags());
      }

      WavefrontInternalReporter wfReporter = new WavefrontInternalReporter.Builder().
          prefixedWith(prefix).withSource(source).withReporterPointTags(pointTags).
          reportMinuteDistribution().build(wavefrontSender);
      return new WavefrontJerseyReporter(wfReporter, reportingIntervalSeconds, wavefrontSender,
              applicationTags, source);
    }
  }

  @Override
  public void start() {
    wfReporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
  }

  @Override
  public void stop() {
    heartbeaterService.close();
    wfReporter.stop();
  }
}
