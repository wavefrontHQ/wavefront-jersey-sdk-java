# Metrics, Histograms and Trace Spans provided by this SDK

Let's consider a RESTful HTTP GET API that returns all the fulfilled orders with the API handler below:

```java
    @ApiOperation(value = "Get all the fulfilled orders")
    @GET
    @Path("/orders/fulfilled")
    public List<Order> getAllFulfilledOrders() {
       ...
    }
```

Assume the API handler is:
1. Part of an **Ordering** application
2. Defined within an **Inventory** microservice
3. Deployed in the **us-west-1** cluster
4. Serviced by the **primary** shard/mirror
5. On source **host-1**
6. And the API call returns a HTTP 200 status code

The following metrics, histograms and spans are reported to Wavefront when this API is invoked:

## Request Gauges
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.request.inventory.orders.fulfilled.GET.inflight|Gauge|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.total_requests.inflight|Gauge|host-1|Ordering|us-west-1|Inventory|primary|n/a|n/a|

## Granular Response Metrics
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.response.inventory.orders.fulfilled.GET.200.cumulative.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_shard.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_service.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|Inventory|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_cluster.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|n/a|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_appliation.count|DeltaCounter|wavefront-provided|Ordering|n/a|n/a|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|

## Granular Response Histograms
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.response.inventory.orders.fulfilled.GET.200.latency|WavefrontHistogram|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.cpu_ns|WavefrontHistogram|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|

## Completed Response Metrics
This includes all the completed requests that returned a response (i.e. success + errors).

|Entity Name| Entity Type|source|application|cluster|service|shard|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|
|jersey.server.response.completed.aggregated_per_source.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.completed.aggregated_per_shard.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.completed.aggregated_per_service.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|n/a|
|jersey.server.response.completed.aggregated_per_cluster.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|n/a|n/a|
|jersey.server.response.completed.aggregated_per_application.count|DeltaCounter|wavefont-provided|Ordering|n/a|n/a|n/a|

## Error Response Metrics
This includes all the completed requests that resulted in an error response (that is HTTP status code of 4xx or 5xx).

|Entity Name| Entity Type|source|application|cluster|service|shard|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|
|jersey.server.response.errors.aggregated_per_source.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.errors.aggregated_per_shard.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.errors.aggregated_per_service.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|n/a|
|jersey.server.response.errors.aggregated_per_cluster.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|n/a|n/a|
|jersey.server.response.errors.aggregated_per_application.count|DeltaCounter|wavefont-provided|Ordering|n/a|n/a|n/a|

## Tracing Spans

Every span will have the operation name as span name and a start time and duration in milliseconds. Additionally the following attributes are included in the generated tracing spans:

| Span Tag Key          | Span Tag Value                         |
| --------------------- | -------------------------------------- |
| traceId               | 4a3dc181-d4ac-44bc-848b-133bb3811c31   |
| parent                | q908ddfe-4723-40a6-b1d3-1e85b60d9016   |
| followsFrom           | b768ddfe-4723-40a6-b1d3-1e85b60d9016   |
| spanId                | c908ddfe-4723-40a6-b1d3-1e85b60d9016   |
| component             | jersey-server                          |
| span.kind             | server                                 |
| application           | OrderingApp                            |
| service               | inventory                              |
| cluster               | us-west-2                              |
| shard                 | secondary                              |
| location              | Oregon (*custom tag)                   |
| env                   | Staging (*custom tag)                  |
| http.method           | GET                                    |
| http.url              | http://{SERVER_ADDR}/orders/fulfilled  |
| http.status_code      | 200                                    |
| error                 | True                                   |
| jersey.path           | "/orders/fulfilled"                    |
| jersey.resource.class | com.sample.ordering.InventoryController |
