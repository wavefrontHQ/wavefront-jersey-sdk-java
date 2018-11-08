# Wavefront Jersey SDK
This document explains the advanced mode to configure Wavefront Jersey SDK.

## Set Up a WavefrontJerseyFilter
This SDK provides a `WavefrontJerseyFilter` for collecting HTTP request/response metrics and histograms. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

The steps for creating a `WavefrontJerseyFilter` are:
1. Create an `ApplicationTags` instance, which specifies metadata about your application.
2. Create a `WavefrontSender` for sending data to Wavefront.
3. Create a `WavefrontJerseyReporter` for reporting Jersey metrics and histograms to Wavefront.
4. Optionally create a `WavefrontTracer` for reporting trace data from Jersey APIs to Wavefront.
5. Create a `WavefrontJerseyFilter`.
6. Register the `WavefrontJerseyFilter`.

For the details of each step, see the sections below.

### 1. Set Up Application Tags
Application tags determine the metadata (point tags and span tags) that are included with every metric/histogram/span reported to Wavefront. These tags enable you to filter and query the reported data in Wavefront.

You encapsulate application tags in an `ApplicationTags` object. See [Instantiating ApplicationTags](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md) for details.

### 2. Set Up a WavefrontSender

A `WavefrontSender` object implements the low-level interface for sending data to Wavefront. You can choose to send data to Wavefront using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

* See [Set Up a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#set-up-a-wavefrontsender) for details on instantiating a proxy or direct ingestion client.

**Note:** If you are using multiple Wavefront Java SDKs, see [Sharing a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md) for information about sharing a single `WavefrontSender` instance across SDKs.

The `WavefrontSender` is used by both the `WavefrontJerseyReporter` and the optional `WavefrontTracer`.

### 3. Create a WavefrontJerseyReporter
A `WavefrontJerseyReporter` object reports metrics and histograms to Wavefront.

To build a `WavefrontJerseyReporter`, you must specify:
* An `ApplicationTags` object ([see above](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#1-set-up-application-tags))
* A `WavefrontSender` object ([see above](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#2-set-up-a-wavefrontsender)).

You can optionally specify:
* A nondefault source for the reported data. If you omit the source, the host name is automatically used.
* A nondefault reporting interval, which controls how often data is reported to the WavefrontSender. The reporting interval determines the timestamps on the data sent to Wavefront. If you omit the reporting interval, data is reported once a minute.

```java

ApplicationTags applicationTags = buildTags(); // pseudocode; see above

// Create WavefrontJerseyReporter.Builder using applicationTags.
WavefrontJerseyReporter.Builder wfJerseyReporterBuilder = new WavefrontJerseyReporter.Builder(applicationTags);

// Optionally set a nondefault source name for your metrics and histograms. Omit this statement to use the host name.
wfJerseyReporterBuilder.withSource("mySource");

// Optionally change the reporting interval to 30 seconds. Default is 1 minute
wfJerseyReporterBuilder.reportingIntervalSeconds(30);

// Create a WavefrontJerseyReporter with a WavefronSender
WavefrontJerseyReporter wfJerseyReporter = wfJerseyReporterBuilder.build(wavefrontSender);
```

### 4. WavefrontTracer (Optional)
You can optionally configure the `WavefrontTracer` to create and send trace data from your Jersey application to Wavefront.

To build a `WavefrontTracer`, you must specify:
* The `ApplicationTags` object ([see above](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#1-set-up-application-tags)).
* A `WavefrontSpanReporter` for reporting trace data to Wavefront. See [Create a WavefrontSpanReporter](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#create-a-wavefrontspanreporter) for details.
  **Note:** When you create the `WavefrontSpanReporter`, you should instantiate it with the same source name and `WavefrontSender` that you used to create the `WavefrontJerseyReporter` (see above).

```java
ApplicationTags applicationTags = buildTags(); // pseudocode; see above
Reporter wavefrontSpanReporter = buildSpanReporter(); // pseudocode
Tracer tracer = new WavefrontTracer.Builder(wavefrontSpanReporter, applicationTags).build();
```

### 5. Create WavefrontJerseyFilter
To build the `WavefrontJerseyFilter`:

```java
// Use the WavefrontJerseyReporter and ApplicationTags to create a builder
WavefrontJerseyFilter.Builder wfJerseyFilterBuilder =
  new WavefrontJerseyFilter.Builder(wfJerseyReporter, applicationTags);

// Set the tracer to optionally send tracing data
wfJerseyFilterBuilder.withTracer(tracer);

// Create the WavefrontJerseyFilter
WavefrontJerseyFilter wfJerseyFilter = wfJerseyFilterBuilder.build();
```


### 6. Register the WavefrontJerseyFilter
After you create the WavefrontJerseyFilter, you must register it. How you do this varies based on the framework you use:

* See [dropwizard.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md) for registering in a dropwizard based application.
* See [springboot.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md) for registering in a springboot based application.

## Start the Jersey Reporter
After you instantiate the `WavefrontJerseyReporter` and `WaveFrontJerseyFilter`, you must explicitly start the Jersey reporter.

```java
// Start the reporter
wfJerseyReporter.start();
```

## Stop the Jersey Reporter

Before you shut down your Jersey application, you must explicitly stop the Jersy reporter.
```java
// Stop the reporter
wfJerseyReporter.stop();
```

## Metrics and Histograms Sent From Jersey Operations

See the [metrics documentation](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/metrics.md) for details on the out of the box metrics and histograms collected by this SDK and reported to Wavefront.

## Cross Process Context Propagation
See the [tracing documentation](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#cross-process-context-propagation) for details on propagating span contexts across process boundaries.

