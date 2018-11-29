# Registering a WavefrontJerseyFilter with Dropwizard

After you create a `WavefrontJerseyFilter` (using [quickstart steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#3-create-and-register-a-wavefrontjerseyfilter) or [custom steps](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#5-create-and-register-a-wavefrontjerseyfilter)), you must register it with your application's Dropwizard environment. 
 

To register a `WavefrontJerseyFilter` in a Dropwizard application:

```java
public class MyApplication extends Application<MyConfiguration>{
    @Override
    public void run(MyApplicationConfiguration configuration, Environment environment) {
        ...
        ...
        // Create the filter 
        WavefrontJerseyFilter wfJerseyFilter = buildJerseyFilter();  // pseudocode

        // Register the filter with Dropwizard Jersey environment
        environment.jersey().register(wfJerseyFilter);
        ...
        ...
    }
}
```

See the [Dropwizard documentation](https://www.dropwizard.io/0.7.1/docs/manual/core.html#jersey-filters) for further details on Jersey filters.
