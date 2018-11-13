package com.wavefront.sdk.jersey;

import com.wavefront.sdk.common.Pair;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.model.Resource;

import javax.ws.rs.container.ContainerResponseContext;
import java.util.Optional;

/**
 * A utils class to generate metric name for Jersey based application requests/responses.
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 */
abstract class MetricNameUtils {

  private static final String REQUEST_PREFIX = "request.";
  private static final String RESPONSE_PREFIX = "response.";

  /**
   * Util to generate metric name from the jersey container request.
   *
   * @param request jersey container request.
   * @return generated metric name from the jersey container request.
   */
  static Optional<Pair<String, String>> metricNameAndPath(ContainerRequest request) {
    return metricNameAndPath(request, REQUEST_PREFIX);
  }

  /**
   * Util to generate metric name from the jersey container response.
   *
   * @param request  jersey container request.
   * @param response jersey container response.
   * @return generated metric name from the jersey container request/response.
   */
  static Optional<String> metricName(ContainerRequest request,
                                     ContainerResponseContext response) {
    Optional<Pair<String, String>> optionalMetricName = metricNameAndPath(request, RESPONSE_PREFIX);
    return optionalMetricName.map(metricName -> metricName._1 + "." + response.getStatus());
  }

  static Optional<Pair<String, String>> metricNameAndPath(ContainerRequest request, String prefix) {
    Resource matchedResource = request.getUriInfo().getMatchedModelResource();
    if (matchedResource != null) {
      StringBuilder matchingPath = new StringBuilder(stripLeadingAndTrailingSlashes(
          matchedResource.getPath()));
      // prepend the path for every parent
      while (matchedResource.getParent() != null) {
        matchedResource = matchedResource.getParent();
        matchingPath.insert(0, stripLeadingAndTrailingSlashes(matchedResource.getPath()) + "/");
      }
      Optional<String> optionalMetricName = metricName(request.getMethod(),
          matchingPath.toString());
      return optionalMetricName.map(metricName -> new Pair<>(prefix + metricName, matchingPath.toString()));
    }
    return Optional.empty();
  }

  /**
   * Accepts a resource method and extracts the path and turns slashes into dots to be more metric
   * friendly. Might return empty metric name if all the original characters in the string are not
   * metric friendly.
   *
   * @param httpMethod Jersey API HTTP request method.
   * @param path       Jersey API request relative path.
   * @return generated metric name from the original request.
   */
  private static Optional<String> metricName(String httpMethod, String path) {
    String metricId = stripLeadingAndTrailingSlashes(path);
    // prevents metrics from trying to create object names with weird characters
    // swagger-ui introduces a route: api-docs/{route: .+} and the colon must be removed
    metricId = metricId.replace('/', '.').replace(":", "").
        replace("{", "_").replace("}", "_");
    if (StringUtils.isBlank(metricId)) {
      return Optional.empty();
    }

    return Optional.of(metricId + "." + httpMethod);
  }

  private static String stripLeadingAndTrailingSlashes(String path) {
    return path == null ? "" : StringUtils.strip(path, "/");
  }
}
