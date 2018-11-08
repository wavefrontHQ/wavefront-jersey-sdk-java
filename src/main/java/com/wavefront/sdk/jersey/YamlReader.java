package com.wavefront.sdk.jersey;

import com.wavefront.config.WavefrontReportingConfig;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.jersey.reporter.WavefrontJerseyReporter;

import org.apache.commons.lang3.BooleanUtils;

import java.io.IOException;

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
public abstract class YamlReader {
  public static WavefrontJerseyFilter constructJerseyFilter(String applicationTagsYamlFile,
                                                            String wfReportingConfigYamlFile) {
    // Step 1 - Create an ApplicationTags instance, which specifies metadata about your application.
    ApplicationTags applicationTags = constructApplicationTags(applicationTagsYamlFile);

    // Step 2 - Construct WavefrontReportingConfig
    WavefrontReportingConfig wfReportingConfig =
        constructWavefrontReportingConfig(wfReportingConfigYamlFile);

    // Step 3 - Create a WavefrontSender for sending data to Wavefront.
    WavefrontSender wavefrontSender = constructWavefrontSender(wfReportingConfig);

    // Step 4 - Create a WavefrontJerseyReporter for reporting
    // Jersey metrics and histograms to Wavefront.
    WavefrontJerseyReporter wfJerseyReporter = new WavefrontJerseyReporter.Builder
            (applicationTags).withSource(wfReportingConfig.getSource()).build(wavefrontSender);

    // Step 5 - Create a WavefrontJerseyFilter.Builder
    WavefrontJerseyFilter.Builder wfJerseyFilterBuilder = new WavefrontJerseyFilter.Builder
            (wfJerseyReporter, applicationTags);

    if (BooleanUtils.isTrue(wfReportingConfig.getReportTraces())) {
      // Step 6 - Optionally create a WavefrontTracer for reporting trace data
      // from Jersey APIs to Wavefront.
      WavefrontSpanReporter wfSpanReporter;
      try {
        wfSpanReporter = new WavefrontSpanReporter.Builder().
            withSource(wfReportingConfig.getSource()).build(wavefrontSender);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      wfJerseyFilterBuilder.withTracer(new WavefrontTracer.Builder(wfSpanReporter,
              applicationTags).build());
    }

    // Step 7 - Start the Jersey reporter to report metrics and histograms
    wfJerseyReporter.start();

    // Step 8 - Create WavefrontJvmReporter.Builder using applicationTags
    WavefrontJvmReporter wfJvmReporter = new WavefrontJvmReporter.Builder(applicationTags).
        withSource(wfReportingConfig.getSource()).build(wavefrontSender);

    // Step 9 - Start the JVM reporter to report JVM metrics
    wfJvmReporter.start();

    // Step 10 - Return the filter that you should register with your Jersey based application.
    return wfJerseyFilterBuilder.build();
  }

}
