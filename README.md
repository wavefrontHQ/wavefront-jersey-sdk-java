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

## Set Up a WavefrontJerseyFilter
This SDK provides a `WavefrontJerseyFilter` for collecting HTTP request/response metrics and histograms. See the [Jersey documentation](https://jersey.github.io/documentation/latest/filters-and-interceptors.html) to understand how filters work.

You can configure this SDK either using [basic mode](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/blob/master/docs/basic-mode.md) or [advanced mode](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java/blob/master/docs/advanced-mode.md).

Option 1: `Basic Mode` - Use this mode if you want to quickly setup the Jersey SDK and instrument your application. This mode will configure Jersey component for you. Since the Jersey based application runs on a JVM, this mode will also configure and instrument JVM component for you. This is the fastest way to instrument your Jersey based application.

</br>
Option 2: `Advanced Mode` - Use this mode if you want a stricter control on how you want to instrument your application. This mode gives you more knobs to configure the SDK depending on your needs. If you are a Wavefront power user, you might want to go with this option.

