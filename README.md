# CodaHale Aggregated Metrics CloudWatch Reporter

This is a CloudWatch Reporter for the stable version of Dropwizard Metrics (formerly CodaHale & Yammer Metrics). The reporter is an implementation of [ScheduledReporter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/ScheduledReporter.html) from Dropwizard Metrics v3.1. 

It reports the metric data to CloudWatch asynchronously using the [AmazonCloudWatchAsyncClient (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/AmazonCloudWatchAsyncClient.html) interface. Each aggregated value in a metric reported individually to CloudWatch.

## Table of Contents

  - [Defaults](#defaults)
  - [Credits](#credits)
  - [Usage](#usage)

### Defaults

The Reporter provides all the same options that the [GraphiteReporter](http://metrics.dropwizard.io/3.1.0/manual/graphite/) does. By default:

- There is no prefix on the Metrics
- Rate metrics are in `TimeUnit.Seconds`
- Duration metrics are in `TimeUnit.Milliseconds`
- `MetricFilter.ALL` will be used for the Filter
- `Clock.defaultClock()` will be used for the Clock
- Each aggregated CodaHale Metric is reported as a separate [MetricDatum (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/MetricDatum.html)
- The reporter adds a `Type` [Dimension (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/Dimension.html) to each reported metric as follows:

| Type                             | Metric Name                                                     |
| -------------------------------- | --------------------------------------------------------------- |
| 1-min-mean-rate-in-seconds       | com.example.component.SomeComponent.arbitrary-some-timer        |
| 1-min-mean-rate-in-seconds       | com.example.component.OtherComponent.arbitrary-other-timer      |
| 95%                              | com.example.component.SomeComponent.arbitrary-some-timer        |
| 95%                              | com.example.component.OtherComponent.arbitrary-other-timer      |


### Usage

The reporter provides a fine-graned configuration options through its builder to configure what metrics should be reported to CloudWatch. Since AWS costs money, you probably do not want to report `all` the values from [Meter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Meter.html) or [Snapshot](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Snapshot.html), but only what's really useful to you

### Credits
* https://github.com/blacklocus/metrics-cloudwatch
* https://github.com/tptodorov/metrics-cloudwatch
* https://github.com/basis-technology-corp/metrics-cloudwatch-reporter
* https://github.com/wavefrontHQ/java/tree/master/dropwizard-metrics/3.1
