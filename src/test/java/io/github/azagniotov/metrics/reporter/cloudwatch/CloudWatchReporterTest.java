package io.github.azagniotov.metrics.reporter.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CloudWatchReporterTest {

    private static final String NAMESPACE = "namespace";

    @Mock
    private AmazonCloudWatchAsyncClient mockAmazonCloudWatchAsyncClient;

    @Mock
    private Future<PutMetricDataResult> mockPutMetricDataResultFuture;

    @Captor
    private ArgumentCaptor<PutMetricDataRequest> metricDataRequestCaptor;

    private MetricRegistry metricRegistry;
    private CloudWatchReporter.Builder reporterBuilder;

    @Before
    public void setUp() throws Exception {
        metricRegistry = new MetricRegistry();
        reporterBuilder = CloudWatchReporter.forRegistry(metricRegistry, mockAmazonCloudWatchAsyncClient, NAMESPACE);
        when(mockAmazonCloudWatchAsyncClient.putMetricDataAsync(metricDataRequestCaptor.capture())).thenReturn(mockPutMetricDataResultFuture);
    }

    @Test
    public void shouldNotInvokeCloudWatchClientWhenDryMode() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.withDryRun().build().report();

        verify(mockAmazonCloudWatchAsyncClient, never()).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void shouldReportWithoutGlobalDimensions() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).hasSize(1);
        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("count"));
    }

    @Test
    public void shouldReportExpectedCounterDimension() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("count"));
    }

    @Test
    public void shouldReportExpectedGaugeDimension() throws Exception {
        metricRegistry.register("blah", (Gauge<Long>) () -> 1L);
        reporterBuilder.build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("gauge"));
    }

    @Test
    public void shouldReportExpectedOneMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter("TheMeter").mark(1);
        reporterBuilder.withOneMinuteMeanRate().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("1-min-mean-rate [per-second]"));
    }

    @Test
    public void shouldReportExpectedFiveMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter("TheMeter").mark(1);
        reporterBuilder.withFiveMinuteMeanRate().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("5-min-mean-rate [per-second]"));
    }

    @Test
    public void shouldReportExpectedFifteenMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter("TheMeter").mark(1);
        reporterBuilder.withFifteenMinuteMeanRate().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("15-min-mean-rate [per-second]"));
    }

    @Test
    public void shouldReportExpectedMeanRateDimension() throws Exception {
        metricRegistry.meter("TheMeter").mark(1);
        reporterBuilder.withMeanRate().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("mean-rate [per-second]"));
    }

    @Test
    public void shouldReportExpectedHistogramArithmeticMeanDimension() throws Exception {
        metricRegistry.histogram("TheHistogram").update(1);
        reporterBuilder.withArithmeticMean().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("snapshot-mean"));
    }

    @Test
    public void shouldReportExpectedHistogramStdDevDimension() throws Exception {
        metricRegistry.histogram("TheHistogram").update(1);
        reporterBuilder.withStdDev().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("snapshot-std-dev"));
    }

    @Test
    public void shouldReportExpectedTimeArithmeticMeanDimension() throws Exception {
        metricRegistry.timer("TheTimer").update(3, TimeUnit.MILLISECONDS);
        reporterBuilder.withArithmeticMean().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("snapshot-mean [in-milliseconds]"));
    }

    @Test
    public void shouldReportExpectedTimerStdDevDimension() throws Exception {
        metricRegistry.timer("TheTimer").update(3, TimeUnit.MILLISECONDS);
        reporterBuilder.withStdDev().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Type").withValue("snapshot-std-dev [in-milliseconds]"));
    }

    @Test
    public void shouldReportExpectedSingleGlobalDimension() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Region").withValue("us-west-2"));
    }

    @Test
    public void shouldReportExpectedMultipleGlobalDimensions() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2", "Instance=stage").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Region").withValue("us-west-2"));
        assertThat(dimensions).contains(new Dimension().withName("Instance").withValue("stage"));
    }

    @Test
    public void shouldNotReportDuplicateGlobalDimensions() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2", "Region=us-west-2").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).containsNoDuplicates();
    }

    @Test
    public void shouldReportCounterValue() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        reporterBuilder.build().report();

        final MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();

        assertThat(metricDatum.getValue()).isWithin(1.0);
    }

    @Test
    public void shouldNotReportUnchangedCounterValue() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        final CloudWatchReporter cloudWatchReporter = reporterBuilder.build();

        cloudWatchReporter.report();
        MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(1);
        metricDataRequestCaptor.getAllValues().clear();

        cloudWatchReporter.report();

        verify(mockAmazonCloudWatchAsyncClient, times(1)).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void shouldReportCounterValueDelta() throws Exception {
        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();
        final CloudWatchReporter cloudWatchReporter = reporterBuilder.build();

        cloudWatchReporter.report();
        MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(2);
        metricDataRequestCaptor.getAllValues().clear();

        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();
        metricRegistry.counter("TheCounter").inc();

        cloudWatchReporter.report();
        metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(6);

        verify(mockAmazonCloudWatchAsyncClient, times(2)).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void shouldReportExpectedHistogramArithmeticMeanAsIs() throws Exception {
        metricRegistry.histogram("TheHistogram").update(1);
        reporterBuilder.withArithmeticMean().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-mean");

        assertThat(metricData.getValue().intValue()).isEqualTo(1);
        assertThat(metricData.getUnit()).isEqualTo("None");
    }

    @Test
    public void shouldReportExpectedTimerArithmeticMeanAsConvertedByDefaultDurationUnit() throws Exception {
        metricRegistry.timer("TheTimer").update(1000000, TimeUnit.NANOSECONDS);
        reporterBuilder.withArithmeticMean().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-mean [in-milliseconds]");

        assertThat(metricData.getValue().intValue()).isEqualTo(1);
        assertThat(metricData.getUnit()).isEqualTo("Milliseconds");
    }

    @Test
    public void shouldReportExpectedHistogramStdDevAsIs() throws Exception {
        metricRegistry.histogram("TheHistogram").update(1);
        metricRegistry.histogram("TheHistogram").update(2);
        metricRegistry.histogram("TheHistogram").update(3);
        metricRegistry.histogram("TheHistogram").update(30);
        reporterBuilder.withStdDev().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-std-dev");

        assertThat(metricData.getValue().intValue()).isEqualTo(12);
        assertThat(metricData.getUnit()).isEqualTo("None");
    }

    @Test
    public void shouldReportExpectedHistogramMaxAsIs() throws Exception {
        metricRegistry.histogram("TheHistogram").update(1);
        metricRegistry.histogram("TheHistogram").update(2);
        metricRegistry.histogram("TheHistogram").update(3);
        metricRegistry.histogram("TheHistogram").update(30);
        reporterBuilder.withStatisticSet().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-summary");

        assertThat(metricData.getStatisticValues().getMaximum().intValue()).isEqualTo(30);
        assertThat(metricData.getStatisticValues().getMinimum().intValue()).isEqualTo(1);
        assertThat(metricData.getUnit()).isEqualTo("None");
    }

    @Test
    public void shouldReportExpectedTimerStdDevAsConvertedByDefaultDurationUnit() throws Exception {
        metricRegistry.timer("TheTimer").update(1000000, TimeUnit.NANOSECONDS);
        metricRegistry.timer("TheTimer").update(2000000, TimeUnit.NANOSECONDS);
        metricRegistry.timer("TheTimer").update(3000000, TimeUnit.NANOSECONDS);
        metricRegistry.timer("TheTimer").update(30000000, TimeUnit.NANOSECONDS);
        reporterBuilder.withStdDev().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-std-dev [in-milliseconds]");

        assertThat(metricData.getValue().intValue()).isEqualTo(12);
        assertThat(metricData.getUnit()).isEqualTo("Milliseconds");
    }

    private MetricDatum metricDatumByDimensionFromCapturedRequest(final String dimensionValue) {
        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        final List<MetricDatum> metricData = putMetricDataRequest.getMetricData();

        final Optional<MetricDatum> metricDatumOptional =
                metricData
                        .stream()
                        .filter(metricDatum -> {
                            System.out.println(metricDatum.getDimensions());
                            return metricDatum.getDimensions()
                                .contains(new Dimension().withName("Type").withValue(dimensionValue));
                        })
                        .findFirst();

        if (metricDatumOptional.isPresent()) {
            return metricDatumOptional.get();
        }

        throw new IllegalStateException("Could not find MetricDatum for Dimension value: " + dimensionValue);
    }

    private MetricDatum firstMetricDatumFromCapturedRequest() {
        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        return putMetricDataRequest.getMetricData().get(0);
    }

    private List<Dimension> firstMetricDatumDimensionsFromCapturedRequest() {
        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        final MetricDatum metricDatum = putMetricDataRequest.getMetricData().get(0);
        return metricDatum.getDimensions();
    }

    private List<Dimension> allDimensionsFromCapturedRequest() {
        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        final List<MetricDatum> metricData = putMetricDataRequest.getMetricData();
        final List<Dimension> all = new LinkedList<>();
        for (final MetricDatum metricDatum : metricData) {
            all.addAll(metricDatum.getDimensions());
        }
        return all;
    }
}
