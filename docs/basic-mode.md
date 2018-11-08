# Wavefront Jersey SDK
This document explains the basic mode to configure Wavefront Jersey SDK.

## Set Up a WavefrontJerseyFilter
This SDK provides a `WavefrontJerseyFilter` for collecting HTTP request/response metrics and histograms. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

The steps for creating a `WavefrontJerseyFilter` are:
1. Set up Application Tags.
2. Set up WavefrontReportingConfig to report out of the box metrics, histograms and traces to Wavefront.
3. Create a `WavefrontJerseyFilter`.
4. Register the `WavefrontJerseyFilter`.

For the details of each step, see the sections below.

### 1. Set Up ApplicationTags

Application tags determine the metadata (point tags and span tags) that are included with every metric/histogram/span reported to Wavefront. These tags enable you to filter and query the reported data in Wavefront.

Best way to inject application tags in your service is via YAML configuration file.
Create `application-tags.yaml` config for your web service in the Jersey application.
```
application: beachshirts
cluster: us-west
service: styling
shard: primary
customTags:
  location: Palo-Alto
  env: production
```
Note: `application` and `service` tags are mandatory whereas `cluster`, `shard` and `customTags` are optional.
</br>
Also, you need to do this for all your services within the Jersey application.

### 2. Set Up WavefrontReportingConfig

You can report out of the box metrics, histograms and traces to Wavefront either via proxy or using direct ingestion. If you are using the proxy, then you need to make sure the proxy is setup correctly and is listening on direct distribution and tracing port.

Create a `wf-reporting-config.yaml` for your web service in the dropwizard application.
Also, you need to do this for all your services within the Jersey application.

Option 1 - Send data to Wavefront via proxy
```
# If you are using proxy, then use the below configuration
reportingMechanism: "proxy"
proxyHost: "<replace-with-wavefront-proxy-hostname>"
proxyMetricsPort: 2878
proxyDistributionsPort: 40000
proxyTracingPort: 30000
source: "<replace-with-reporting-source>"
reportTraces: true
```
Note: this assumes that the proxy is listening on ports `2878` for metrics, `40000` for histograms and `30000` for traces. Also if you wish to avoid instrumenting traces then set `reportTraces` to false.

Option 2 - Send data to Wavefront via direct ingestion

```
# If you are using direct ingestion, then use the below configuration
reportingMechanism: "direct"
server: "<replace-with-wavefront-url>"
token: "<replace-with-wavefront-api-token>"
source: "<replace-with-reporting-source>"
reportTraces: true
```
Note: If you wish to avoid instrumenting traces then set `reportTraces` to false.

### 3. Create a WavefrontJerseyFilter

Make sure your pass in the above YAML files to the Jersey application to instantiate WavefrontJerseyFilter.
```java
    // Instantiate the WavefrontJerseyFilter
    WavefrontJerseyFilter wavefrontJerseyFilter =
            YamlReader.constructJerseyFilter(applicationTagsYamlFile, wfReportingConfigYamlFile);
```

### 4. Register the WavefrontJerseyFilter
After you create the WavefrontJerseyFilter, you must register it. How you do this varies based on the framework you use:

* See [dropwizard.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md) for registering in a dropwizard based application.
* See [springboot.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md) for registering in a springboot based application.

## Metrics and Histograms Sent From Jersey Operations

See the [metrics documentation](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/metrics.md) for details on the out of the box metrics and histograms collected by this SDK and reported to Wavefront.

## Cross Process Context Propagation
See the [tracing documentation](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#cross-process-context-propagation) for details on propagating span contexts across process boundaries.

