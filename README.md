```
VMware has ended active development of this project, this repository will no longer be updated.
```
# Wavefront Jersey SDK [![build status][ci-img]][ci] [![Released Version][maven-img]][maven]

The Wavefront for VMware Jersey SDK for Java is a library that collects out-of-the-box metrics, histograms and (optionally) traces from your Jersey-based microservices application, and reports the data to Wavefront. You can analyze the data in [Wavefront](https://www.wavefront.com) to better understand how your application is performing in production.

You use this SDK for applications that use Jersey-compliant frameworks such as Dropwizard, Spring Boot, etc.


## Maven
If you are using Maven, add the following maven dependency to your pom.xml:
```
<dependency>
    <groupId>com.wavefront</groupId>
    <artifactId>wavefront-jersey-sdk-java</artifactId>
    <version>$releaseVersion</version>
</dependency>
```

Replace `$releaseVersion` with the latest version available on [maven](http://search.maven.org/#search%7Cga%7C1%7Cwavefront-jersey-sdk-java).

## Setup Steps

Choose the setup option that best fits your use case.

* [**Option 1: Quickstart**](#quickstart) - Use configuration files, plus a few code changes, to quickly instrument the Jersey framework and the JVM in your microservice. Default settings are used.

* [**Option 2: Custom Setup**](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/blob/master/docs/custom.md) - Instantiate helper objects in your code for control over all settable aspects of instrumentation. Appropriate for Wavefront power users.

## Quickstart

Follow the steps below to quickly set up a `WavefrontJerseyFilter` for collecting HTTP request/response metrics, histograms, and trace data. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work. 

For each service that uses a Jersey-compliant framework, [add the dependency](#maven) if you have not already done so, and then perform the following steps:

1. [Configure a set of application tags](#1-configure-application-tags) to describe your application to Wavefront.
2. [Configure how to report](#2-configure-wavefront-reporting) out-of-the-box metrics, histograms and trace data to Wavefront.
3. [Create and register a `WavefrontJerseyFilter`](#3-create-and-register-a-wavefrontjerseyfilter) object.
4. *Optional*. [Create and register a `WavefrontJaxrsClientFilter`](#4-create-and-register-a-wavefrontjaxrsclientfilter-optional) object.

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
    **Notes:**

    | YAML Property | Description |
    | ---- | ---- |
    | `application`  |  Required. Name that identifies your application. Use the same value for all microservices in the same application.|
    | `service` | Required. Name that identifies the microservice within your application. Use a unique value for each microservice.  |
    | `cluster` | Optional. Name of a group of related hosts that serves as a cluster or region in which the application will run. |
    | `shard` | Optional. Name of a subgroup of hosts within a cluster. |
    | `customTags` | Optional. Tags specific to your application. |

### 2. Configure Wavefront Reporting

You can choose to report out-of-the-box metrics, histograms, and traces to Wavefront using one of the following techniques:
* Use [direct ingestion](https://docs.wavefront.com/direct_ingestion.html) to send the data directly to the Wavefront service. This is the simplest way to get up and running quickly. See [Option 1](#option-1---send-data-directly-to-wavefront).
* Use a [Wavefront proxy](https://docs.wavefront.com/proxies.html), which then forwards the data to the Wavefront service. This is the recommended choice for a large-scale deployment that needs resilience to internet outages, control over data queuing and filtering, and more. See [Option 2](#option-2---send-data-to-a-wavefront-proxy).


#### Option 1 - Send Data Directly to Wavefront

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
    **Notes:**

    | YAML Property | Value |
    | ---- | ---- |
    | `reportingMechanism`  |  `direct` |
    |  `server` | URL for your Wavefront instance, typically `https://myCompany.wavefront.com` | 
    | `token` | String produced by [obtaining an API token](http://docs.wavefront.com/wavefront_api.html#generating-an-api-token). You must have Direct Data Ingestion permission when you obtain the token.  |  
    | `source`  | String that represents where the data originates -- typically the host name of the machine running the microservice.  |  
    | `reportTraces`  | `true` to include trace data. `false` to suppress trace data.  |  


#### Option 2 - Send Data to a Wavefront Proxy

**Note:** Before your application can send data, you must [set up a Wavefront proxy](https://github.com/wavefrontHQ/java/tree/master/proxy#set-up-a-wavefront-proxy) on a host that the application can access.


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
    **Notes:**  
    When you [set up a Wavefront proxy](https://github.com/wavefrontHQ/java/tree/master/proxy#set-up-a-wavefront-proxy) on the specified proxy host, you specify the port it will listen to for each type of data to be sent. The proxy port properties in the YAML file must specify the same port numbers as the corresponding properties in the proxy configuration file (`wavefront.conf`):


    | YAML Property | Value |
    | ---- | ---- |
    | `reportingMechanism`  |  `proxy` | 
    | `proxyHost`  | String name or IP address of the host on which you set up the Wavefront proxy.  |  
    |  `proxyMetricsPort` | Proxy port to send metrics to. Default is 2878. Must match the value set for `pushListenerPorts=` in `wavefront.conf`. | 
    | `proxyDistributionsPort` | Proxy port to send histograms to.  Recommended value is 40000. Must match the value set for  `histogramDistListenerPorts=` in `wavefront.conf`. |  
    |  `proxyTracingPort` | Proxy port to send trace data to. Recommended value is 30000. Must match the value set for `traceListenerPorts=` in `wavefront.conf`.  |  
    | `source`  | String that represents where the data originates -- typically, the host name of the machine running the microservice.  |  
    | `reportTraces`  | `true` to include trace data. `false` to suppress trace data.  |  

### 3. Create and Register a WavefrontJerseyFilter

In the code for each web service in your Jersey application:

1. Instantiate a `WavefrontJerseyFactory`. Pass in the path names of the configuration files you created above.
    ```java
    // Instantiate the WavefrontJerseyFactory
    WavefrontJerseyFactory wavefrontJerseyFactory = new WavefrontJerseyFactory(
        applicationTagsYamlFile, wfReportingConfigYamlFile);
    ```
2. Use the factory to create a `WavefrontJerseyFilter`:
    ```java
    // Create the WavefrontJerseyFilter
    WavefrontJerseyFilter wavefrontJerseyFilter
        wavefrontJerseyFactory.getWavefrontJerseyFilter();
    ```
3. Register the `WavefrontJerseyFilter` according to the framework used by the service:
    * [Dropwizard registration steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/dropwizard.md)
    * [Spring Boot registration steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/springboot.md)


### 4. Create and Register a WavefrontJaxrsClientFilter (Optional)

_Ignore this section if you are collecting only metrics and histograms (without trace data) from your application._

In the code for each web service that is a JAX-RS-based client: 

1. Use the factory you built in the [previous section](#3-create-and-register-a-wavefrontjerseyfilter) to create a  [WavefrontJaxrsClientFilter](https://github.com/wavefrontHQ/wavefront-jaxrs-sdk-java): 

    ```java
    // Instantiate the WavefrontJaxrsClientFilter
    WavefrontJaxrsClientFilter wavefrontJaxrsClientFilter = wavefrontJerseyFactory.
        getWavefrontJaxrsClientFilter();
    ```

2. Register the `WavefrontJaxrsClientFilter`:

    ```Java
    // Assumes a JAX-RS-compliant ClientBuilder instance
    clientBuilder.register(filter);
    ```

**Notes:** 
* The `WavefrontJaxrsClientFilter` enables an instrumented client service to propagate trace information when sending a request to another service. 
* The `WavefrontJaxrsClientFilter` supplements the `WavefrontJerseyFilter`, which  creates server-side trace data, but not client-side trace data. 


## Metrics and Histograms Sent From Jersey Operations

See the [metrics documentation](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/tree/master/docs/metrics.md) for details on the out of the box metrics and histograms collected by this SDK and reported to Wavefront.


[ci-img]: https://travis-ci.com/wavefrontHQ/wavefront-jersey-sdk-java.svg?branch=master
[ci]: https://travis-ci.com/wavefrontHQ/wavefront-jersey-sdk-java
[maven-img]: https://img.shields.io/maven-central/v/com.wavefront/wavefront-jersey-sdk-java.svg?maxAge=604800
[maven]: http://search.maven.org/#search%7Cga%7C1%7Cwavefront-jersey-sdk-java
