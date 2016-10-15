# CodaHale Aggregated Metrics CloudWatch Reporter

This is a CloudWatch Reporter for the stable version of Dropwizard Metrics (formerly CodaHale & Yammer Metrics). The reporter is an implementation of [ScheduledReporter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/ScheduledReporter.html) from Dropwizard Metrics v3.1. 

## Table of Contents

  - [Summary](#summary)
  - [Defaults](#defaults)
  - [Dependencies](#dependencies)
  - [Usage](#usage)
  - [Credits](#credits)
  - [License](#license)

### Summary

This CloudWatchReporter reports the metric data to CloudWatch asynchronously using the [AmazonCloudWatchAsyncClient (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/AmazonCloudWatchAsyncClient.html) interface. 

Each value in CodeHale [Metric](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Metric.html) is reported as a separate [MetricDatum (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/MetricDatum.html). Each [Snapshot](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Snapshot.html) translated into [StatisticSet (AWS)] (http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/StatisticSet.html) in the most direct way possible.

When reporting [Meter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Meter.html), [Counter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Counter.html), [Histogram](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Histogram.html) and [Timer](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Timer.html) count metrics (`getCount()`) as [MetricDatum (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/MetricDatum.html), only the count difference since last report is reported. This way the counters do not require a reset within the application using this reporter.

### Defaults

The Reporter uses the following defaults which can be configured:

- Rate metrics are in `TimeUnit.Seconds`
- Duration metrics are in `TimeUnit.Milliseconds`
- `MetricFilter.ALL` will be used for the Filter
- `Clock.defaultClock()` will be used for the Clock
- Empty global [Dimension (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/Dimension.html) list
- The reporter adds a `Type` [Dimension (AWS)](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudwatch/model/Dimension.html) to each reported metric as follows:

| Type                             | Metric Name                                                     |
| -------------------------------- | --------------------------------------------------------------- |
| 1-min-mean-rate-in-seconds       | com.example.component.SomeComponent.arbitrary-some-timer        |
| 1-min-mean-rate-in-seconds       | com.example.component.OtherComponent.arbitrary-other-timer      |
| 95%                              | com.example.component.SomeComponent.arbitrary-some-timer        |
| 95%                              | com.example.component.OtherComponent.arbitrary-other-timer      |


### Dependencies
```
dependencies { 
    compile("io.dropwizard.metrics:metrics-core:3.1.0")
    compile("com.amazonaws:aws-java-sdk-cloudwatch:1.11.41")
    compile("com.google.guava:guava:19.0")
}
```


### Usage

The reporter provides a fine-graned configuration options through its builder to configure what metrics should be reported to CloudWatch. Since AWS costs money, you probably do not want to report `all` the values from [Meter](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Meter.html) or [Snapshot](http://metrics.dropwizard.io/3.1.0/apidocs/com/codahale/metrics/Snapshot.html), but only what's really useful to you

### Credits
* https://github.com/blacklocus/metrics-cloudwatch
* https://github.com/tptodorov/metrics-cloudwatch
* https://github.com/basis-technology-corp/metrics-cloudwatch-reporter
* https://github.com/wavefrontHQ/java/tree/master/dropwizard-metrics/3.1


### License
MIT
