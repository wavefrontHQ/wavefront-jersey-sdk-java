# Configuring Springboot

Once you have created a [WavefrontJerseyFilter](https://github.com/wavefrontHQ/wavefront-jersey-sdk-java#5-create-wavefrontjerseyfilter), register it in your springboot application as follows:

Include the `spring-boot-starter-jersey` as a maven dependency and then add a @Bean of type `ResourceConfig` where you register all the endpoints as shown in the example below:

```java
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
                ...
                ...
                // Create the filter as specified in the link above
                WavefrontJerseyFilter wfJerseyFilter = buildJerseyFilter();

                // register WavefrontJerseyFilter
                register(wavefrontJerseyFilter);

                // Register your endpoint controller
                register(<your-controller>);
                ...
                ...
    }
}
```

See the [springboot documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-developing-web-applications.html#boot-features-jersey) for further details on Jersey filters.
