# Wavefront Jersey SDK
This page provides custom steps for setting up the Wavefront by VMware Jersey SDK in your application. With custom setup, you instantiate helper objects explicitly in your code instead of using the configuration files shown with [Quickstart setup](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#quickstart).

Custom setup gives you control over all settable aspects of instrumenting the Jersey framework in a microservice. You should use custom setup if you want to tune performance through the `WavefrontSender`, or if you want to implement your own configuration-file mechanism.  

## Custom Setup
Follow the steps below to set up a `WavefrontJerseyFilter` for collecting HTTP request/response metrics, histograms, and trace data. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

For each service that uses a Jersey-compliant framework, [add the dependency](#maven) if you have not already done so, and then perform the following steps:

1. [Create an `ApplicationTags` instance](#1-set-up-application-tags), which specifies metadata about your application.
2. [Create a `WavefrontSender`](#2-set-up-a-wavefrontsender) for sending data to Wavefront.
3. [Create a `WavefrontJerseyReporter`](#3-create-a-wavefrontjerseyreporter) for reporting Jersey metrics and histograms to Wavefront.
4. *Optional*. [Create a `WavefrontTracer`](#4-set-up-a-wavefronttracer-optional) for reporting trace data from Jersey APIs to Wavefront.
5. [Create and register a `WavefrontJerseyFilter`](#5-create-and-register-a-wavefrontjerseyfilter).
6. *If you created a `WavefrontTracer` in step 4,* [create and register a `WavefrontJaxrsClientFilter`](#6-create-and-register-a-wavefrontjaxrsclientfilter).

For the details of each step, see the sections below.

### 1. Set Up Application Tags
Application tags determine the metadata (point tags and span tags) that are included with every metric/histogram/span reported to Wavefront. These tags enable you to filter and query the reported data in Wavefront.

You encapsulate application tags in an `ApplicationTags` object. See [Instantiating ApplicationTags](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md) for details.

### 2. Set Up a WavefrontSender

A `WavefrontSender` object implements the low-level interface for sending data to Wavefront. You can choose to send data using either the [Wavefront proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html).

* If you have already set up a `WavefrontSender` for another SDK that will run in the same JVM, use that one.  (For details about sharing a `WavefrontSender` instance, see [Share a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md#share-a-wavefrontsender).)

* Otherwise, follow the steps in [Set Up a WavefrontSender](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md#set-up-a-wavefrontsender).

The `WavefrontSender` is used by both the `WavefrontJerseyReporter` and the optional `WavefrontTracer`.

### 3. Create a WavefrontJerseyReporter
A `WavefrontJerseyReporter` object reports metrics and histograms to Wavefront.

To build a `WavefrontJerseyReporter`, you must specify:
* An `ApplicationTags` object ([see above](#1-set-up-application-tags))
* A `WavefrontSender` object ([see above](2-set-up-a-wavefrontsender)).

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

### 4. Set Up a WavefrontTracer (Optional)
You can optionally configure the `WavefrontTracer` to create and send trace data from your Jersey application to Wavefront.

To build a `WavefrontTracer`, you must specify:
* The `ApplicationTags` object ([see above](#1-set-up-application-tags)).
* A `WavefrontSpanReporter` for reporting trace data to Wavefront. See [Create a WavefrontSpanReporter](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#create-a-wavefrontspanreporter) for details.
  **Note:** When you create the `WavefrontSpanReporter`, you should instantiate it with the same source name and `WavefrontSender` that you used to create the `WavefrontJerseyReporter` earlier on this page.

```java
ApplicationTags applicationTags = buildTags(); // pseudocode; see above
Reporter wavefrontSpanReporter = buildSpanReporter(); // pseudocode

Tracer wavefrontTracer = new WavefrontTracer.Builder(wavefrontSpanReporter, applicationTags).build();
```

### 5. Create and Register a WavefrontJerseyFilter

A  `WavefrontJerseyFilter` collects HTTP request/response metrics, histograms, and server-side trace data. 

1. Build a `WavefrontJerseyFilter`. Specify the `ApplicationTags`,  `WavefrontSpanReporter`, and optional `WavefrontTracer` objects you created above:

    ```java
    // Use the WavefrontJerseyReporter and ApplicationTags to create a builder
    WavefrontJerseyFilter.Builder wfJerseyFilterBuilder =
        new WavefrontJerseyFilter.Builder(wfJerseyReporter, applicationTags);

    // Configure the builder with the WavefrontTracer, if you set one up. 
    // Omit this call if you only want to collect metrics and histograms.
    wfJerseyFilterBuilder.withTracer(wavefrontTracer);

    // Create the WavefrontJerseyFilter
    WavefrontJerseyFilter wfJerseyFilter = wfJerseyFilterBuilder.build();
    ```

2. Register the `WavefrontJerseyFilter`. Follow the steps for the framework used by the service:

    - [Dropwizard registration steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md)
    - [Spring Boot registration steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md)

### 6. Create and Register a WavefrontJaxrsClientFilter

_Ignore this section if you want to collect only metrics and histograms (no trace data)._ 

A [`WavefrontJaxrsClientFilter`](https://github.com/wavefrontHQ/wavefront-jaxrs-sdk-java) enables an instrumented client-side service to propagate trace information when sending a request to another service. 
The `WavefrontJaxrsClientFilter` supplements the `WavefrontJerseyFilter`, which  creates server-side trace data, but not client-side trace data. 

**Note:** As an alternative to using a `WavefrontJaxrsClientFilter`, you can [propagate trace information between services explicitly](#cross-process-context-propagation).

1. Build the `WavefrontJaxrsClientFilter`. Specify the `WavefrontSender`, `ApplicationTags` and `WavefrontTracer` you created above, and use the same source name you specified to the `WavefrontJerseyReporter`.

    ```Java
    sourceName = "mySource"; // Example - Replace value!
    WavefrontJaxrsClientFilter wfClientFilter = new WavefrontJaxrsClientFilter(wavefrontSender,  
      applicationTags, sourceName, wavefrontTracer);
    ```

2. Register the `WavefrontJaxrsClientFilter`:
    ```Java
    // Assumes a JAX-RS-compliant ClientBuilder instance
    clientBuilder.register(wfClientFilter);
    ```


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
