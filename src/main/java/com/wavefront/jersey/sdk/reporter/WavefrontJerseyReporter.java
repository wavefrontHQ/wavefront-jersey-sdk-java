package com.wavefront.jersey.sdk.reporter;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;
import com.wavefront.jersey.sdk.ApplicationTags;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.entities.metrics.WavefrontMetricSender;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static com.wavefront.jersey.sdk.Constants.HEART_BEAT_METRIC;
import static com.wavefront.jersey.sdk.Constants.JERSEY_SERVER_COMPONENT;
import static com.wavefront.jersey.sdk.Constants.NULL_TAG_VAL;

/**
 * Wavefront reporter for your Jersey based application responsible for reporting metrics and
 * histograms out of the box for you.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyReporter implements JerseyReporter, Runnable {

  private static final Logger logger =
      Logger.getLogger(WavefrontJerseyReporter.class.getCanonicalName());
  private final WavefrontInternalReporter wfReporter;
  private final WavefrontMetricSender wavefrontMetricSender;
  private final ApplicationTags applicationTags;
  private final String source;

  private WavefrontJerseyReporter(WavefrontInternalReporter wfReporter,
                                  WavefrontMetricSender wavefrontMetricSender,
                                  ApplicationTags applicationTags,
                                  String source) {
    this.wfReporter = wfReporter;
    this.wavefrontMetricSender = wavefrontMetricSender;
    this.applicationTags = applicationTags;
    this.source = source;
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("jersey-sdk-heart-beater"));
    scheduler.scheduleAtFixedRate(this, 1, 5, TimeUnit.MINUTES);
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

  @Override
  public void run() {
    try {
      wavefrontMetricSender.sendMetric(HEART_BEAT_METRIC, 1.0, System.currentTimeMillis(), source,
          new HashMap<String, String>() {{
            put("application", applicationTags.getApplication());
            put("cluster", applicationTags.getCluster() == null ? NULL_TAG_VAL :
                applicationTags.getCluster());
            put("service", applicationTags.getService());
            put("shard", applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard());
            put("component", JERSEY_SERVER_COMPONENT);
          }});
    } catch (IOException e) {
      logger.warning("Cannot report " + HEART_BEAT_METRIC + " to Wavefront");
    }
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
      wfReporter.start(reportingIntervalSeconds, TimeUnit.SECONDS);
      return new WavefrontJerseyReporter(wfReporter, wavefrontSender, applicationTags, source);
    }
  }
}
