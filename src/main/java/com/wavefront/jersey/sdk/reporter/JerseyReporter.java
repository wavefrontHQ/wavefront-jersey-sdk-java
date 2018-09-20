package com.wavefront.jersey.sdk.reporter;

import com.wavefront.internal_reporter_java.io.dropwizard.metrics5.MetricName;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * An interface to report metrics and histograms for your Jersey based application.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public interface JerseyReporter {
  /**
   * Increment the counter metric.
   *
   * @param metricName         Name of the Counter to be reported.
   */
  void incrementCounter(MetricName metricName);

  /**
   * Increment the delta counter
   *
   * @param metricName         Name of the Delta Counter to be reported.
   */
  void incrementDeltaCounter(MetricName metricName);

  /**
   * Update the histogram metric with the input latency.
   *
   * @param metricName         Name of the histogram to be reported.
   * @param latencyMillis      API latency in millis.
   */
  void updateHistogram(MetricName metricName, long latencyMillis);

  /**
   * Register Integer Gauge so that it is reported to Wavefront.
   *
   * @param metricName         Name of the gauge to be reported.
   * @param value              Value of the gauge.
   */
  void registerGauge(MetricName metricName, AtomicInteger value);
}
