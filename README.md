# Wavefront Jersey SDK

The Wavefront for VMware Jersey SDK for Java is a library that collects out-of-the-box metrics, histograms and (optionally) traces from your Jersey-based microservices application, and reports the data to Wavefront. You can analyze the data in [Wavefront](https://www.wavefront.com) to better understand how your application is performing in production.

You use this SDK for applications that use Jersey-compliant frameworks such as Dropwizard, Spring Boot, etc.


## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-jersey-sdk-java</artifactId>
    <version>0.9.0</version>
</dependency>
```

## Setup Steps

Choose the setup option that best fits your use case.

* [**Option 1: Quickstart**](#quickstart) - Use configuration files, plus a few code changes, to quickly instrument the Jersey framework and the JVM in your microservice. Default settings are used.

* [**Option 2: Custom Setup**](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/blob/master/docs/custom.md) - Instantiate helper objects in your code for complete control over all settable aspects of instrumenting the Jersey framework in a microservice. Appropriate for Wavefront power users.

## Quickstart

Follow the steps below to quickly set up a `WavefrontJerseyFilter` for collecting HTTP request/response metrics and histograms. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

For each service that uses a Jersey-compliant framework:

1. Configure a set of application tags to describe your application to Wavefront.
2. Configure how out-of-the-box metrics, histograms and traces are reported to Wavefront.
3. Create a `WavefrontJerseyFilter` object.
4. Register the `WavefrontJerseyFilter` object.

For the details of each step, see the sections below.

### 1. Configure Application Tags

Application tags determine the metadata (point tags and span tags) that are included with every metric/histogram/span reported to Wavefront. These tags enable you to filter and query the reported data in Wavefront.

For each web service in your Jersey application:

1. Create a `application-tags.yaml` configuration file.
2. Edit the file to add properties and values such as the following:
    ```
    application: "beachshirts"
    cluster: "us-west"
    service: "styling"
    shard: "primary"
    customTags:
      location: "Palo-Alto"
      env: "production"
    ```
    **Note:** 
    * `application` is required. Use the same value for all microservices in the same application.
    * `service` is required. Use a unique value for each microservice in the application.  
    * `cluster`, `shard` and `customTags` are optional and can be omitted.

### 2. Configure Wavefront Reporting

You can choose to report out-of-the-box metrics, histograms, and traces to Wavefront either through a Wavefront proxy or through direct ingestion. 

**Option 1 - Send data to a Wavefront proxy**

Make sure your Wavefront proxy is [installed](http://docs.wavefront.com/proxies_installing.html) and [configured to listen on ports](http://docs.wavefront.com/proxies_installing.html#configuring-proxy-ports-for-metrics-histograms-and-traces) for metrics, histogram distributions, and trace data. 

For each web service in your Jersey application:
1. Create a `wf-reporting-config.yaml` configuration file.
2. Edit the file to add properties and values such as the following:
    ```
    # Reporting with a Wavefront proxy
    reportingMechanism: "proxy"
    proxyHost: "<replace-with-wavefront-proxy-hostname>"
    proxyMetricsPort: 2878
    proxyDistributionsPort: 40000
    proxyTracingPort: 30000
    source: "<replace-with-reporting-source>"
    reportTraces: true
    ```
    **Note:**  This example assumes you set up a proxy to listen on ports `2878` for metrics, `40000` for histogram distributions, and `30000` for trace data.
    
    **Note:** You can suppress trace data by setting `reportTraces` to false.

**Option 2 - Send data directly to the Wavefront service**

You'll need to identify the URL for your Wavefront instance and [obtain an API token](http://docs.wavefront.com/wavefront_api.html#generating-an-api-token).

For each web service in your Jersey application:
1. Create a `wf-reporting-config.yaml` configuration file.
2. Edit the file to add properties and values such as the following:
    ```
    # Reporting through direct ingestion
    reportingMechanism: "direct"
    server: "<replace-with-wavefront-url>"
    token: "<replace-with-wavefront-api-token>"
    source: "<replace-with-reporting-source>"
    reportTraces: true
    ```
    **Note:** You can suppress trace data by setting `reportTraces` to false.

### 3. Create a WavefrontJerseyFilter

In the code for each web service in your Jersey application:
* Instantiate a `WavefrontJerseyFilter` object. Pass in the configuration files you created above.
    ```java
    // Instantiate the WavefrontJerseyFilter
    WavefrontJerseyFactory wavefrontJerseyFactory = new WavefrontJerseyFactory(
        applicationTagsYamlFile, wfReportingConfigYamlFile);
    WavefrontJerseyFilter wavefrontJerseyFilter = wavefrontJerseyFactory.getWavefrontJerseyFilter();
    ```

* *Optional*: You can also get [WavefrontJaxrsClientFilter](https://github.com/wavefrontHQ/wavefront-jaxrs-sdk-java) from the `WavefrontJerseyFactory` for instrumenting your JAX-RS-based client to form a complete trace.

* ```java
    // Instantiate the WavefrontJaxrsClientFilter
    WavefrontJaxrsClientFilter wavefrontJaxrsClientFilter = wavefrontJerseyFactory.
        getWavefrontJaxrsClientFilter();
    ```

### 4. Register the WavefrontJerseyFilter
After you create the WavefrontJerseyFilter, you must register it. How you do this varies based on the framework you are instrumenting:

* See [dropwizard.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md) for registering in a dropwizard based application.
* See [springboot.md](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md) for registering in a springboot based application.

## Metrics and Histograms Sent From Jersey Operations

See the [metrics documentation](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/metrics.md) for details on the out of the box metrics and histograms collected by this SDK and reported to Wavefront.

## Cross Process Context Propagation
See the [tracing documentation](https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java#cross-process-context-propagation) for details on propagating span contexts across process boundaries.
