# Configuring Dropwizard

Once you have created a [WavefrontJerseyFilter](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#5-create-wavefrontjerseyfilter), register it in your dropwizard application as follows:

```java
public class MyApplication extends Application<MyConfiguration>{
    @Override
    public void run(MyApplicationConfiguration configuration, Environment environment) {
        ...
        ...
        // Create the filter as specified in the link above
        WavefrontJerseyFilter wfJerseyFilter = buildJerseyFilter();

        // Register the filter with Dropwizard Jersey Environment
        environment.jersey().register(wfJerseyFilter);
        ...
        ...
    }
}
```

See the [dropwizard documentation](https://www.dropwizard.io/0.7.1/docs/manual/core.html#jersey-filters) for further details on Jersey filters.
