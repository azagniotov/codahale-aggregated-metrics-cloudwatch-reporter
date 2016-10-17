import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter;
import io.github.azagniotov.metrics.reporter.cloudwatch.CloudWatchReporter.Percentile;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final int TEN_MINUTES = 10;
    private static final int ONE_MINUTE = 60 * 1000;

    public static void main(final String[] args) throws Exception {

        final ExecutorService executors = Executors.newCachedThreadPool();
        executors.submit(new ReportingApp());

        for (int idx = 0; idx < TEN_MINUTES; idx++) {
            System.out.printf("Sleeping... %d minutes elapsed%n", idx);
            Thread.sleep(ONE_MINUTE);
        }
        executors.shutdownNow();
        executors.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static final class ReportingApp implements Callable<Void> {

        private final MetricRegistry metricRegistry;
        private final Timer theTimer;

        ReportingApp() {
            metricRegistry = new MetricRegistry();
            theTimer = metricRegistry.timer("TheTimer");

            final AmazonCloudWatchAsync amazonCloudWatchAsync = new AmazonCloudWatchAsyncClient();
            amazonCloudWatchAsync.setRegion(Region.getRegion(Regions.US_WEST_2));

            final CloudWatchReporter cloudWatchReporter =
                    CloudWatchReporter.forRegistry(metricRegistry, amazonCloudWatchAsync, Main.class.getName())
                            .convertRatesTo(TimeUnit.SECONDS)
                            .convertDurationsTo(TimeUnit.MILLISECONDS)
                            .filter(MetricFilter.ALL)
                            .withPercentiles(Percentile.P75, Percentile.P99)
                            .withOneMinuteMeanRate()
                            .withFiveMinuteMeanRate()
                            .withFifteenMinuteMeanRate()
                            .withMeanRate()
                            .withArithmeticMean()
                            .withStdDev()
                            .withStatisticSet()
                            .withJvmMetrics()
                            .withGlobalDimensions("Region=us-west-2", "Instance=stage")
                            .withDryRun()
                            .build();

            cloudWatchReporter.start(10, TimeUnit.SECONDS);
        }

        @Override
        public Void call() throws Exception {

            int runs = 1;
            while (runs <= 100) {
                metricRegistry.counter("TheCounter").inc();
                metricRegistry.meter("TheMeter").mark(runs);
                metricRegistry.histogram("TheHistogram").update(runs);
                Timer.Context context = theTimer.time();
                Thread.sleep(250);
                context.stop();
                ++runs;

                Thread.sleep(1000);
            }

            return null;
        }
    }
}
