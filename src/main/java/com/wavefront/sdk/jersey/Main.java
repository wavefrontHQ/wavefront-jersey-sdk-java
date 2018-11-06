package com.wavefront.sdk.jersey;

/**
 * // TODO - javadoc
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
public class Main {
  public static void main(String[] args) {
    WavefrontJerseyFilter proxyFilter = YamlReader.constructJerseyFilter(
        "/Users/sushant/wavefront/wavefront-jersey-sdk-java/application-tags.yaml",
        "/Users/sushant/wavefront/wavefront-jersey-sdk-java/proxy-reporting.yaml", true);

    WavefrontJerseyFilter directFilter = YamlReader.constructJerseyFilter(
        "/Users/sushant/wavefront/wavefront-jersey-sdk-java/application-tags.yaml",
        "/Users/sushant/wavefront/wavefront-jersey-sdk-java/direct-reporting.yaml", true);
  }
}
