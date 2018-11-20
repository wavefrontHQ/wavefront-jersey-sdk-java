package com.wavefront.sdk.jersey;

import com.wavefront.config.ApplicationTagsConfig;
import com.wavefront.config.WavefrontReportingConfig;
import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.direct.ingestion.WavefrontDirectIngestionClient;
import com.wavefront.sdk.jaxrs.client.WavefrontJaxrsClientFilter;
import com.wavefront.sdk.jersey.reporter.WavefrontJerseyReporter;
import com.wavefront.sdk.proxy.WavefrontProxyClient;

import org.apache.commons.lang3.BooleanUtils;

import java.io.File;
import java.io.IOException;

import io.opentracing.Tracer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;

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
  private final WavefrontJvmReporter wfJvmReporter;
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
      try {
        wfSpanReporter = new WavefrontSpanReporter.Builder().
            withSource(source).build(wavefrontSender);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      tracer = new WavefrontTracer.Builder(wfSpanReporter, applicationTags).build();
      wfJerseyFilterBuilder.withTracer(tracer);
    } else {
      tracer = null;
    }

    // Step 7 - Start the Jersey reporter to report metrics and histograms
    wfJerseyReporter.start();

    // Step 8 - Create WavefrontJvmReporter.Builder using applicationTags
    this.wfJvmReporter = new WavefrontJvmReporter.Builder(applicationTags).
        withSource(source).build(wavefrontSender);

    // Step 9 - Start the JVM reporter to report JVM metrics
    wfJvmReporter.start();

    // Step 10 - Construct the filter that you should register with your Jersey based application.
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

  public WavefrontJvmReporter getWavefrontJvmReporter() {
    return wfJvmReporter;
  }

  /**
   * Construct {@link WavefrontSender) from {@link WavefrontReportingConfig}
   * TODO: Remove once new version of wavefront-internal-reporter-java is released
   */
  private static WavefrontSender constructWavefrontSender(
      WavefrontReportingConfig wfReportingConfig) {
    String reportingMechanism = wfReportingConfig.getReportingMechanism();
    switch (reportingMechanism) {
      case WavefrontReportingConfig.proxyReporting:
        return new WavefrontProxyClient.Builder(wfReportingConfig.getProxyHost()).
            metricsPort(wfReportingConfig.getProxyMetricsPort()).
            distributionPort(wfReportingConfig.getProxyDistributionsPort()).
            tracingPort(wfReportingConfig.getProxyTracingPort()).build();
      case WavefrontReportingConfig.directReporting:
        return new WavefrontDirectIngestionClient.Builder(
            wfReportingConfig.getServer(), wfReportingConfig.getToken()).build();
      default:
        throw new RuntimeException("Invalid reporting mechanism:" + reportingMechanism);
    }
  }

  /**
   * Construct {@link ApplicationTags} from given path of YAML file
   * TODO: Remove once new version of wavefront-internal-reporter-java is released
   */
  private static ApplicationTags constructApplicationTags(String applicationTagsYamlFile) {
    YAMLFactory factory = new YAMLFactory(new ObjectMapper());
    YAMLParser parser;
    try {
      parser = factory.createParser(new File(applicationTagsYamlFile));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ApplicationTagsConfig applicationTagsConfig;
    try {
      applicationTagsConfig = parser.readValueAs(ApplicationTagsConfig.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    ApplicationTags.Builder applicationTagsBuilder = new ApplicationTags.Builder(
        applicationTagsConfig.getApplication(), applicationTagsConfig.getService());

    if (applicationTagsConfig.getCluster() != null) {
      applicationTagsBuilder.cluster(applicationTagsConfig.getCluster());
    }

    if (applicationTagsConfig.getShard() != null) {
      applicationTagsBuilder.shard(applicationTagsConfig.getShard());
    }

    if (applicationTagsConfig.getCustomTags() != null) {
      applicationTagsBuilder.customTags(applicationTagsConfig.getCustomTags());
    }

    return applicationTagsBuilder.build();
  }

  /**
   * Construct {@link WavefrontReportingConfig} from given path of YAML file
   * TODO: Remove once new version of wavefront-internal-reporter-java is released
   */
  private static WavefrontReportingConfig constructWavefrontReportingConfig(
      String wfReportingConfigYamlFile) {
    YAMLFactory factory = new YAMLFactory(new ObjectMapper());
    YAMLParser parser;
    try {
      parser = factory.createParser(new File(wfReportingConfigYamlFile));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      return parser.readValueAs(WavefrontReportingConfig.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
