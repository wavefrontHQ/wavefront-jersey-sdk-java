# Configuring Springboot

After you create a `WavefrontJerseyFilter` (using [quickstart steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#3-create-and-register-a-wavefrontjerseyfilter) or [custom steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#5-create-and-register-a-wavefrontjerseyfilter)), you must register it in your Spring Boot application.

To register a `WavefrontJerseyFilter` in a Spring Boot application:

1. Include `spring-boot-starter-jersey` as a Maven dependency.
2. Add a @Bean of type `ResourceConfig` where you register all the endpoints, as shown in the following example:

```java
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
                ...
                ...
                // Create the filter 
                WavefrontJerseyFilter wfJerseyFilter = buildJerseyFilter();  // pseudocode

                // register WavefrontJerseyFilter
                register(wavefrontJerseyFilter);

                // Register your endpoint controller
                register(<your-controller>);
                ...
                ...
    }
}
```

See the [Spring Boot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-developing-web-applications.html#boot-features-jersey) for further details on Jersey filters.
