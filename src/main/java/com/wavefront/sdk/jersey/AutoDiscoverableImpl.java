package com.wavefront.sdk.jersey;

import org.glassfish.jersey.internal.spi.AutoDiscoverable;

import javax.ws.rs.core.FeatureContext;

public class AutoDiscoverableImpl implements AutoDiscoverable {

  @Override
  public void configure(FeatureContext featureContext) {
    featureContext.register(new WavefrontJerseyFactory().getWavefrontJerseyFilter());
  }
}
