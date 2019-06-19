package io.github.azagniotov.metrics.reporter.cloudwatch;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.codahale.metrics.EWMA;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SlidingWindowReservoir;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.amazonaws.services.cloudwatch.model.StandardUnit.Count;
import static com.amazonaws.services.cloudwatch.model.StandardUnit.Microseconds;
import static com.amazonaws.services.cloudwatch.model.StandardUnit.Milliseconds;
import static com.amazonaws.services.cloudwatch.model.StandardUnit.None;
import static com.google.common.truth.Truth.assertThat;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_COUNT;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_GAUGE;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_NAME_TYPE;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_SNAPSHOT_MEAN;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_SNAPSHOT_STD_DEV;
import static io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.DIMENSION_SNAPSHOT_SUMMARY;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CloudWatchReporterTest {

    private static final String NAMESPACE = "namespace";
    private static final String ARBITRARY_COUNTER_NAME = "TheCounter";
    private static final String ARBITRARY_METER_NAME = "TheMeter";
    private static final String ARBITRARY_HISTOGRAM_NAME = "TheHistogram";
    private static final String ARBITRARY_TIMER_NAME = "TheTimer";
    private static final String ARBITRARY_GAUGE_NAME = "TheGauge";

    @Mock
    private AmazonCloudWatchAsyncClient mockAmazonCloudWatchAsyncClient;

    @Mock
    private Future<PutMetricDataResult> mockPutMetricDataResultFuture;

    @Captor
    private ArgumentCaptor<PutMetricDataRequest> metricDataRequestCaptor;

    private MetricRegistry metricRegistry;
    private CloudWatchReporter.Builder reporterBuilder;

    @BeforeClass
    public static void beforeClass() throws Exception {
        reduceMeterDefaultTickInterval();
    }

    @Before
    public void setUp() throws Exception {
        metricRegistry = new MetricRegistry();
        reporterBuilder = CloudWatchReporter.forRegistry(metricRegistry, mockAmazonCloudWatchAsyncClient, NAMESPACE);
        when(mockAmazonCloudWatchAsyncClient.putMetricDataAsync(metricDataRequestCaptor.capture())).thenReturn(mockPutMetricDataResultFuture);
    }

    @Test
    public void shouldNotInvokeCloudWatchClientInDryRunMode() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.withDryRun().build().report();

        verify(mockAmazonCloudWatchAsyncClient, never()).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void notSettingHighResolutionGeneratesMetricsWithStorageResolutionSetToSixty() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.build().report();

        final MetricDatum firstMetricDatum = firstMetricDatumFromCapturedRequest();
        
        assertThat(firstMetricDatum.getStorageResolution()).isEqualTo(60);
    }

    @Test
    public void settingHighResolutionGeneratesMetricsWithStorageResolutionSetToOne() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.withHighResolution().build().report();

        final MetricDatum firstMetricDatum = firstMetricDatumFromCapturedRequest();

        assertThat(firstMetricDatum.getStorageResolution()).isEqualTo(1);
    }

    @Test
    public void shouldReportWithoutGlobalDimensionsWhenGlobalDimensionsNotConfigured() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.build().report(); // When 'withGlobalDimensions' was not called

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).hasSize(1);
        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(DIMENSION_COUNT));
    }

    @Test
    public void reportedCounterShouldContainExpectedDimension() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(DIMENSION_COUNT));
    }

    @Test
    public void reportedGaugeShouldContainExpectedDimension() throws Exception {
        metricRegistry.register(ARBITRARY_GAUGE_NAME, (Gauge<Long>) () -> 1L);
        reporterBuilder.build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(DIMENSION_GAUGE));
    }

    @Test
    public void shouldNotReportGaugeWhenMetricValueNotOfTypeNumber() throws Exception {
        metricRegistry.register(ARBITRARY_GAUGE_NAME, (Gauge<String>) () -> "bad value type");
        reporterBuilder.build().report();

        verify(mockAmazonCloudWatchAsyncClient, never()).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void neverReportMetersCountersGaugesWithZeroValues() throws Exception {
        metricRegistry.register(ARBITRARY_GAUGE_NAME, (Gauge<Long>) () -> 0L);
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(0);
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc(0);

        buildReportWithSleep(reporterBuilder
                .withArithmeticMean()
                .withOneMinuteMeanRate()
                .withFiveMinuteMeanRate()
                .withFifteenMinuteMeanRate()
                .withMeanRate());

        verify(mockAmazonCloudWatchAsyncClient, never()).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void reportMetersCountersGaugesWithZeroValuesOnlyWhenConfigured() throws Exception {
        metricRegistry.register(ARBITRARY_GAUGE_NAME, (Gauge<Long>) () -> 0L);
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(0);
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc(0);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(-1L, TimeUnit.NANOSECONDS);

        buildReportWithSleep(reporterBuilder
                .withArithmeticMean()
                .withOneMinuteMeanRate()
                .withFiveMinuteMeanRate()
                .withFifteenMinuteMeanRate()
                .withZeroValuesSubmission()
                .withMeanRate());

        verify(mockAmazonCloudWatchAsyncClient, times(1)).putMetricDataAsync(metricDataRequestCaptor.capture());

        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        final List<MetricDatum> metricData = putMetricDataRequest.getMetricData();
        for (final MetricDatum metricDatum : metricData) {
            assertThat(metricDatum.getValue()).isEqualTo(0.0);
        }
    }

    @Test
    public void reportedMeterShouldContainExpectedOneMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(1);
        buildReportWithSleep(reporterBuilder.withOneMinuteMeanRate());

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("1-min-mean-rate [per-second]"));
    }

    @Test
    public void reportedMeterShouldContainExpectedFiveMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(1);
        buildReportWithSleep(reporterBuilder.withFiveMinuteMeanRate());

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("5-min-mean-rate [per-second]"));
    }

    @Test
    public void reportedMeterShouldContainExpectedFifteenMinuteMeanRateDimension() throws Exception {
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(1);
        buildReportWithSleep(reporterBuilder.withFifteenMinuteMeanRate());

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("15-min-mean-rate [per-second]"));
    }

    @Test
    public void reportedMeterShouldContainExpectedMeanRateDimension() throws Exception {
        metricRegistry.meter(ARBITRARY_METER_NAME).mark(1);
        reporterBuilder.withMeanRate().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("mean-rate [per-second]"));
    }

    @Test
    public void reportedHistogramShouldContainExpectedArithmeticMeanDimension() throws Exception {
        metricRegistry.histogram(ARBITRARY_HISTOGRAM_NAME).update(1);
        reporterBuilder.withArithmeticMean().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(DIMENSION_SNAPSHOT_MEAN));
    }

    @Test
    public void reportedHistogramShouldContainExpectedStdDevDimension() throws Exception {
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(1);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(2);
        reporterBuilder.withStdDev().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(DIMENSION_SNAPSHOT_STD_DEV));
    }

    @Test
    public void reportedTimerShouldContainExpectedArithmeticMeanDimension() throws Exception {
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(3, TimeUnit.MILLISECONDS);
        reporterBuilder.withArithmeticMean().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("snapshot-mean [in-milliseconds]"));
    }

    @Test
    public void reportedTimerShouldContainExpectedStdDevDimension() throws Exception {
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(1, TimeUnit.MILLISECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(3, TimeUnit.MILLISECONDS);
        reporterBuilder.withStdDev().build().report();

        final List<Dimension> dimensions = allDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue("snapshot-std-dev [in-milliseconds]"));
    }

    @Test
    public void shouldReportExpectedSingleGlobalDimension() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Region").withValue("us-west-2"));
    }

    @Test
    public void shouldReportExpectedGlobalAndCustomDimensions() throws Exception {


        metricRegistry.counter(DimensionedName.withName(ARBITRARY_COUNTER_NAME)
            .withDimension("key1", "value1")
            .withDimension("key2", "value2")
            .build().encode()).inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Region").withValue("us-west-2"));
        assertThat(dimensions).contains(new Dimension().withName("key1").withValue("value1"));
        assertThat(dimensions).contains(new Dimension().withName("key2").withValue("value2"));
    }

    @Test
    public void shouldReportExpectedMultipleGlobalDimensions() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2", "Instance=stage").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).contains(new Dimension().withName("Region").withValue("us-west-2"));
        assertThat(dimensions).contains(new Dimension().withName("Instance").withValue("stage"));
    }

    @Test
    public void shouldNotReportDuplicateGlobalDimensions() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.withGlobalDimensions("Region=us-west-2", "Region=us-west-2").build().report();

        final List<Dimension> dimensions = firstMetricDatumDimensionsFromCapturedRequest();

        assertThat(dimensions).containsNoDuplicates();
    }

    @Test
    public void shouldReportExpectedCounterValue() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        reporterBuilder.build().report();

        final MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();

        assertThat(metricDatum.getValue()).isWithin(1.0);
        assertThat(metricDatum.getUnit()).isEqualTo(Count.toString());
    }

    @Test
    public void shouldNotReportUnchangedCounterValue() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
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
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        final CloudWatchReporter cloudWatchReporter = reporterBuilder.build();

        cloudWatchReporter.report();
        MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(2);
        metricDataRequestCaptor.getAllValues().clear();

        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();

        cloudWatchReporter.report();
        metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(6);

        verify(mockAmazonCloudWatchAsyncClient, times(2)).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    @Test
    public void shouldReportArithmeticMeanAfterConversionByDefaultDurationWhenReportingTimer() throws Exception {
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(1_000_000, TimeUnit.NANOSECONDS);
        reporterBuilder.withArithmeticMean().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-mean [in-milliseconds]");

        assertThat(metricData.getValue().intValue()).isEqualTo(1);
        assertThat(metricData.getUnit()).isEqualTo(Milliseconds.toString());
    }

    @Test
    public void shouldReportStdDevAfterConversionByDefaultDurationWhenReportingTimer() throws Exception {
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(1_000_000, TimeUnit.NANOSECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(2_000_000, TimeUnit.NANOSECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(3_000_000, TimeUnit.NANOSECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(30_000_000, TimeUnit.NANOSECONDS);
        reporterBuilder.withStdDev().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest("snapshot-std-dev [in-milliseconds]");

        assertThat(metricData.getValue().intValue()).isEqualTo(12);
        assertThat(metricData.getUnit()).isEqualTo(Milliseconds.toString());
    }

    @Test
    public void shouldReportSnapshotValuesAfterConversionByCustomDurationWhenReportingTimer() throws Exception {
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(1, TimeUnit.SECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(2, TimeUnit.SECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(3, TimeUnit.SECONDS);
        metricRegistry.timer(ARBITRARY_TIMER_NAME).update(30, TimeUnit.SECONDS);
        reporterBuilder.withStatisticSet().convertDurationsTo(TimeUnit.MICROSECONDS).build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_SUMMARY);

        assertThat(metricData.getStatisticValues().getSum().intValue()).isEqualTo(36_000_000);
        assertThat(metricData.getStatisticValues().getMaximum().intValue()).isEqualTo(30_000_000);
        assertThat(metricData.getStatisticValues().getMinimum().intValue()).isEqualTo(1_000_000);
        assertThat(metricData.getStatisticValues().getSampleCount().intValue()).isEqualTo(4);
        assertThat(metricData.getUnit()).isEqualTo(Microseconds.toString());
    }

    @Test
    public void shouldReportArithmeticMeanWithoutConversionWhenReportingHistogram() throws Exception {
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(1);
        reporterBuilder.withArithmeticMean().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_MEAN);

        assertThat(metricData.getValue().intValue()).isEqualTo(1);
        assertThat(metricData.getUnit()).isEqualTo(None.toString());
    }

    @Test
    public void shouldReportStdDevWithoutConversionWhenReportingHistogram() throws Exception {
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(1);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(2);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(3);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(30);
        reporterBuilder.withStdDev().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_STD_DEV);

        assertThat(metricData.getValue().intValue()).isEqualTo(12);
        assertThat(metricData.getUnit()).isEqualTo(None.toString());
    }

    @Test
    public void shouldReportSnapshotValuesWithoutConversionWhenReportingHistogram() throws Exception {
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(1);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(2);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(3);
        metricRegistry.histogram(CloudWatchReporterTest.ARBITRARY_HISTOGRAM_NAME).update(30);
        reporterBuilder.withStatisticSet().build().report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_SUMMARY);

        assertThat(metricData.getStatisticValues().getSum().intValue()).isEqualTo(36);
        assertThat(metricData.getStatisticValues().getMaximum().intValue()).isEqualTo(30);
        assertThat(metricData.getStatisticValues().getMinimum().intValue()).isEqualTo(1);
        assertThat(metricData.getStatisticValues().getSampleCount().intValue()).isEqualTo(4);
        assertThat(metricData.getUnit()).isEqualTo(None.toString());
    }

    @Test
    public void shouldReportHistogramSubsequentSnapshotValues_SumMaxMinValues() throws Exception {
        CloudWatchReporter reporter = reporterBuilder.withStatisticSet().build();

        final Histogram slidingWindowHistogram = new Histogram(new SlidingWindowReservoir(4));
        metricRegistry.register("SlidingWindowHistogram", slidingWindowHistogram);

        slidingWindowHistogram.update(1);
        slidingWindowHistogram.update(2);
        slidingWindowHistogram.update(30);
        reporter.report();

        final MetricDatum metricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_SUMMARY);

        assertThat(metricData.getStatisticValues().getMaximum().intValue()).isEqualTo(30);
        assertThat(metricData.getStatisticValues().getMinimum().intValue()).isEqualTo(1);
        assertThat(metricData.getStatisticValues().getSampleCount().intValue()).isEqualTo(3);
        assertThat(metricData.getStatisticValues().getSum().intValue()).isEqualTo(33);
        assertThat(metricData.getUnit()).isEqualTo(None.toString());

        slidingWindowHistogram.update(4);
        slidingWindowHistogram.update(100);
        slidingWindowHistogram.update(5);
        slidingWindowHistogram.update(6);
        reporter.report();

        final MetricDatum secondMetricData = metricDatumByDimensionFromCapturedRequest(DIMENSION_SNAPSHOT_SUMMARY);

        assertThat(secondMetricData.getStatisticValues().getMaximum().intValue()).isEqualTo(100);
        assertThat(secondMetricData.getStatisticValues().getMinimum().intValue()).isEqualTo(4);
        assertThat(secondMetricData.getStatisticValues().getSampleCount().intValue()).isEqualTo(4);
        assertThat(secondMetricData.getStatisticValues().getSum().intValue()).isEqualTo(115);
        assertThat(secondMetricData.getUnit()).isEqualTo(None.toString());
    }

    @Test
    public void shouldNotReportCounterValueDeltaWhenReportingRawCountValue() throws Exception {
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        final CloudWatchReporter cloudWatchReporter = reporterBuilder.withReportRawCountValue().build();

        cloudWatchReporter.report();
        MetricDatum metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(2);
        metricDataRequestCaptor.getAllValues().clear();

        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();
        metricRegistry.counter(ARBITRARY_COUNTER_NAME).inc();

        cloudWatchReporter.report();
        metricDatum = firstMetricDatumFromCapturedRequest();
        assertThat(metricDatum.getValue().intValue()).isEqualTo(8);

        verify(mockAmazonCloudWatchAsyncClient, times(2)).putMetricDataAsync(any(PutMetricDataRequest.class));
    }

    private MetricDatum metricDatumByDimensionFromCapturedRequest(final String dimensionValue) {
        final PutMetricDataRequest putMetricDataRequest = metricDataRequestCaptor.getValue();
        final List<MetricDatum> metricData = putMetricDataRequest.getMetricData();

        final Optional<MetricDatum> metricDatumOptional =
                metricData
                        .stream()
                        .filter(metricDatum -> metricDatum.getDimensions()
                                .contains(new Dimension().withName(DIMENSION_NAME_TYPE).withValue(dimensionValue)))
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

    private void buildReportWithSleep(final CloudWatchReporter.Builder cloudWatchReporterBuilder) throws InterruptedException {
        final CloudWatchReporter cloudWatchReporter = cloudWatchReporterBuilder.build();
        Thread.sleep(10);
        cloudWatchReporter.report();
    }

    /**
     * This is a very ugly way to fool the {@link EWMA} by reducing the default tick interval
     * in {@link Meter} from {@code 5} seconds to {@code 1} millisecond in order to ensure that
     * exponentially-weighted moving average rates are populated. This helps to verify that all
     * the expected {@link Dimension}s are present in {@link MetricDatum}.
     *
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     * @see Meter#tickIfNecessary()
     * @see MetricDatum#getDimensions()
     */
    private static void reduceMeterDefaultTickInterval() throws NoSuchFieldException, IllegalAccessException {
        setFinalStaticField(Meter.class, "TICK_INTERVAL", TimeUnit.MILLISECONDS.toNanos(1));
    }

    private static void setFinalStaticField(final Class clazz, final String fieldName, long value) throws NoSuchFieldException, IllegalAccessException {
        final Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        final Field modifiers = field.getClass().getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, value);
    }
}
