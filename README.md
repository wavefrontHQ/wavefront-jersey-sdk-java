# Wavefront Jersey SDK

This SDK collects out of the box metrics, histograms and optionally traces from your Jersey based microservices application and reports the data to Wavefront. Data can be sent to Wavefront using either the [proxy](https://docs.wavefront.com/proxies.html) or [direct ingestion](https://docs.wavefront.com/direct_ingestion.html). You can analyze the data in [Wavefront](https://www.wavefront.com) to better understand how your application is performing in production.

## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-jersey-sdk-java</artifactId>
    <version>0.9.0</version>
</dependency>
```

## WavefrontJerseyFilter
This SDK provides a `WavefrontJerseyFilter` for collecting HTTP request/response metrics and histograms. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

The steps to create a `WavefrontJerseyFilter` are:
1. Create an instance of `ApplicationTags`: metadata about your application
2. Create a `WavefrontSender`: low-level interface that handles sending data to Wavefront
3. Create a `WavefrontJerseyReporter` for reporting Jersey metrics and histograms to Wavefront
4. Optionally create a `WavefrontTracer` for reporting Jersey traces to Wavefront
5. Finally create a `WavefrontJerseyFilter`

The sections below detail each of the above steps.

### 1. Application Tags
ApplicationTags determine the metadata (aka point/span tags) that are included with every metrics/histograms/spans reported to Wavefront.

See the [documentation](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/apptags.md) for details on instantiating ApplicationTags.

### 2. WavefrontSender

Both the `WavefrontJerseyReporter` and the `WavefrontTracer` require a WavefrontSender: A low-level interface that knows how to send data to Wavefront. See the [Wavefront sender documentation](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/README.md#set-up-a-wavefrontsender) for details on instantiating a proxy or direct ingestion client.

**Note:** See this [documentation](https://github.com/wavefrontHQ/wavefront-sdk-java/blob/master/docs/sender.md) on sharing a single WavefrontSender instance across multiple SDKs.

Once you have a Wavefront sender, create the WavefrontJerseyReporter and optionally the WavefrontTracer.

### 3. WavefrontJerseyReporter
To create the `WavefrontJerseyReporter`:
```java

// Create WavefrontJerseyReporter.Builder using applicationTags.
WavefrontJerseyReporter.Builder builder = new WavefrontJerseyReporter.Builder(applicationTags);

// Set the source for your metrics and histograms
builder.withSource("mySource");

// The reporting interval controls how often data is reported to the WavefrontSender
// and therefore determines the timestamps on the data sent to Wavefront.
// Optionally change the reporting frequency to 30 seconds, defaults to 1 min */
builder.reportingIntervalSeconds(30);

// Create a WavefrontJerseyReporter using ApplicationTags metadata and WavefronSender
WavefrontJerseyReporter wfJerseyReporter =
  new WavefrontJerseyReporter.Builder(applicationTags).build(wavefrontSender);
```
Replace the source `mySource` with a relevant source name.

### 4. WavefrontTracer (Optional)
You can optionally configure the `WavefrontTracer` to send traces from your Jersey application to Wavefront.

To enable sending traces from the SDK, we need to instantiate a WavefrontTracer:

```java
Tracer tracer = new WavefrontTracer.Builder(wavefrontSpanReporter, applicationTags).build();
```
The concept of `applicationTags` is described above. In order to correctly instantiate WavefrontSpanReporter, please refer to this page (https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#wavefrontspanreporter) for more details.

### 5. Create WavefrontJerseyFilter
Finally create the `WavefrontJerseyFilter`:

```java
// Use the WavefrontJerseyReporter and ApplicationTags to create a builder
WavefrontJerseyFilter.Builder wfJerseyFilterBuilder =
  new WavefrontJerseyFilter.Builder(wfJerseyReporter, applicationTags);

// Set the tracer to optionally send tracing data
wfJerseyFilterBuilder.withTracer(tracer);

// Create the WavefrontJerseyFilter
// Can be used in Jersey based (Dropwizard, Springboot etc.) applications
WavefrontJerseyFilter wfJerseyFilter = wfJerseyFilterBuilder.build();
```

### Starting and stopping the reporter
```java
// Once the reporter and filter is instantiated, start the reporter
wfJerseyReporter.start();

// Before shutting down your Jersey application, stop your reporter
wfJerseyReporter.stop();
```

## Register the WavefrontJerseyFilter
Once you have created the WavefrontJerseyFilter, you need to register it. How you do this varies based on the framework you use.

* Refer [dropwizard.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md) for registering in a dropwizard based application.
* Refer [springboot.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md) for registering in a springboot based application.

## Metrics and Histograms collected from your Jersey based application

See the [metrics documentation](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/metrics.md) for details on the out of the box metrics and histograms collected by this SDK and reported to Wavefront.
