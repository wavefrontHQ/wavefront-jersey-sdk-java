package com.wavefront.sdk.jersey;

import com.wavefront.config.WavefrontReportingConfig;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.jaxrs.client.WavefrontJaxrsClientFilter;
import com.wavefront.sdk.jersey.reporter.WavefrontJerseyReporter;

import org.apache.commons.lang3.BooleanUtils;

import io.opentracing.Tracer;

import static com.wavefront.config.ReportingUtils.constructApplicationTags;
import static com.wavefront.config.ReportingUtils.constructWavefrontReportingConfig;
import static com.wavefront.config.ReportingUtils.constructWavefrontSender;

/**
 * A basic mode to configure Jersey server SDK and report Jersey metrics, histograms and tracing
 * spans to Wavefront. Since the Jersey application runs inside a JVM, the basic mode will also
 * configure the JVM SDK and report JVM metrics to Wavefront.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class WavefrontJerseyFactory {
  private final ApplicationTags applicationTags;
  private final String source;
  private final Tracer tracer;
  private final WavefrontSender wavefrontSender;
  private final WavefrontJerseyReporter wfJerseyReporter;
  private final WavefrontJerseyFilter wavefrontJerseyFilter;
  private final WavefrontJaxrsClientFilter wavefrontJaxrsClientFilter;

  /**
   * Construct WavefrontJerseyFactory with given yaml files path of application tags and Wavefront
   * reporting configuration.
   */
  public WavefrontJerseyFactory(String applicationTagsYamlFile, String wfReportingConfigYamlFile) {

    // Step 1 - Create an ApplicationTags instance, which specifies metadata about your application.
    this.applicationTags = constructApplicationTags(applicationTagsYamlFile);

    // Step 2 - Construct WavefrontReportingConfig
    WavefrontReportingConfig wfReportingConfig =
        constructWavefrontReportingConfig(wfReportingConfigYamlFile);

    this.source = wfReportingConfig.getSource();

    // Step 3 - Create a WavefrontSender for sending data to Wavefront.
    this.wavefrontSender = constructWavefrontSender(wfReportingConfig);

    // Step 4 - Create a WavefrontJerseyReporter for reporting
    // Jersey metrics and histograms to Wavefront.
    this.wfJerseyReporter = new WavefrontJerseyReporter.Builder
        (applicationTags).withSource(source).build(wavefrontSender);

    // Step 5 - Create a WavefrontJerseyFilter.Builder
    WavefrontJerseyFilter.Builder wfJerseyFilterBuilder = new WavefrontJerseyFilter.Builder
        (wfJerseyReporter, applicationTags);

    if (BooleanUtils.isTrue(wfReportingConfig.getReportTraces())) {
      // Step 6 - Optionally create a WavefrontTracer for reporting trace data
      // from Jersey APIs to Wavefront.
      WavefrontSpanReporter wfSpanReporter;
      wfSpanReporter = new WavefrontSpanReporter.Builder().withSource(source).build(wavefrontSender);
      tracer = new WavefrontTracer.Builder(wfSpanReporter, applicationTags).build();
      wfJerseyFilterBuilder.withTracer(tracer);
    } else {
      tracer = null;
    }

    // Step 7 - Start the Jersey reporter to report metrics and histograms
    wfJerseyReporter.start();

    // Step 8 - Construct the filter that you should register with your Jersey based application.
    this.wavefrontJerseyFilter = wfJerseyFilterBuilder.build();

    this.wavefrontJaxrsClientFilter = new WavefrontJaxrsClientFilter(wavefrontSender,
        applicationTags, source, tracer);
  }

  public WavefrontJerseyFilter getWavefrontJerseyFilter() {
    return wavefrontJerseyFilter;
  }

  public WavefrontJaxrsClientFilter getWavefrontJaxrsClientFilter() {
    return wavefrontJaxrsClientFilter;
  }

  public ApplicationTags getApplicationTags() {
    return applicationTags;
  }

  public String getSource() {
    return source;
  }

  public Tracer getTracer() {
    return tracer;
  }

  public WavefrontSender getWavefrontSender() {
    return wavefrontSender;
  }

  public WavefrontJerseyReporter getWavefrontJerseyReporter() {
    return wfJerseyReporter;
  }

}
