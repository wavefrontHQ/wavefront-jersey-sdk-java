## Out of the box metrics and histograms for your Jersey based application.

Let's say your have a RESTful HTTP GET API that returns all the fulfilled orders. Let's assume this API is defined in inventory service for your Ordering application.
Below is the API handler for the HTTP GET method.
```java
    @ApiOperation(value = "Get all the fulfilled orders")
    @GET
    @Path("/orders/fulfilled")
    public List<Order> getAllFulfilledOrders() {
       ...
    }
```

Let's assume this HTTP handler is
1) part of 'Ordering' application
2) running inside 'Inventory' microservice
3) deployed in 'us-west-1' cluster
4) serviced by 'primary' shard
5) on source = host-1
6) this API returns HTTP 200 status code

When this API is invoked following entities (i.e. metrics and histograms) are reported directly from your application to Wavefront.

### Request Gauges
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.request.inventory.orders.fulfilled.GET.inflight|Gauge|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.total_requests.inflight|Gauge|host-1|Ordering|us-west-1|Inventory|primary|n/a|n/a|

### Granular Response related metrics
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.response.inventory.orders.fulfilled.GET.200.cumulative.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_shard.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_service.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|Inventory|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_cluster.count|DeltaCounter|wavefront-provided|Ordering|us-west-1|n/a|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.aggregated_per_appliation.count|DeltaCounter|wavefront-provided|Ordering|n/a|n/a|n/a|com.ordering.InventoryWebResource|getAllFulfilledOrders|

### Granular Response related histograms
|Entity Name| Entity Type|source|application|cluster|service|shard|jersey.resource.class|jersey.resource.method|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|-----:|-----:|
|jersey.server.response.inventory.orders.fulfilled.GET.200.latency|WavefrontHistogram|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|
|jersey.server.response.inventory.orders.fulfilled.GET.200.cpu_ns|WavefrontHistogram|host-1|Ordering|us-west-1|Inventory|primary|com.ordering.InventoryWebResource|getAllFulfilledOrders|

### Overall Response related metrics
This includes all the completed requests that returned a response (i.e. success + errors).

|Entity Name| Entity Type|source|application|cluster|service|shard|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|
|jersey.server.response.completed.aggregated_per_source.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.completed.aggregated_per_shard.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.completed.aggregated_per_service.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|n/a|
|jersey.server.response.completed.aggregated_per_cluster.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|n/a|n/a|
|jersey.server.response.completed.aggregated_per_application.count|DeltaCounter|wavefont-provided|Ordering|n/a|n/a|n/a|

### Overall Error Response related metrics
This includes all the completed requests that resulted in an error response (that is HTTP status code of 4xx or 5xx).

|Entity Name| Entity Type|source|application|cluster|service|shard|
| ------------- |:-------------:| -----:|-----:|-----:|-----:|-----:|
|jersey.server.response.errors.aggregated_per_source.count|Counter|host-1|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.errors.aggregated_per_shard.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|primary|
|jersey.server.response.errors.aggregated_per_service.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|Inventory|n/a|
|jersey.server.response.errors.aggregated_per_cluster.count|DeltaCounter|wavefont-provided|Ordering|us-west-1|n/a|n/a|
|jersey.server.response.errors.aggregated_per_application.count|DeltaCounter|wavefont-provided|Ordering|n/a|n/a|n/a|

### Tracing Spans

Every span will have the operation name as span name, start time in millisec along with duration in millisec. The following table includes all the rest attributes of generated tracing spans.  

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
| http.status_code      | 502                                    |
| error                 | True                                   |
| jersey.path           | "/orders/fulfilled"                    |
| jersey.resource.class | com.sample.ordering.InventoryController |
