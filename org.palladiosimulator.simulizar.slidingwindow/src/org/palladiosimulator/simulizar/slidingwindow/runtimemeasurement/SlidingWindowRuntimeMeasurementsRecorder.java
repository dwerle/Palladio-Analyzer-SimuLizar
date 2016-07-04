package org.palladiosimulator.simulizar.slidingwindow.runtimemeasurement;

import java.util.Arrays;
import java.util.Objects;

import javax.measure.Measure;
import javax.measure.quantity.Quantity;

import org.palladiosimulator.edp2.util.MetricDescriptionUtility;
import org.palladiosimulator.measurementframework.MeasuringValue;
import org.palladiosimulator.metricspec.NumericalBaseMetricDescription;
import org.palladiosimulator.metricspec.constants.MetricDescriptionConstants;
import org.palladiosimulator.monitorrepository.MeasurementSpecification;
import org.palladiosimulator.recorderframework.IRecorder;
import org.palladiosimulator.recorderframework.config.IRecorderConfiguration;
import org.palladiosimulator.runtimemeasurement.RuntimeMeasurementModel;
import org.palladiosimulator.simulizar.metrics.PRMRecorder;

/**
 * This class is responsible for propagating sliding window based measurements from
 * {@link MeasurementSpecification}s to the RuntimeMeasurementModel (formerly known as PRM).<br>
 * Examples of such measurements are power or energy consumption measurements, or the sliding window
 * based computation of utilization.<br>
 * 
 * @author Florian Rosenthal
 *
 */
public class SlidingWindowRuntimeMeasurementsRecorder extends PRMRecorder implements IRecorder {

    private static final NumericalBaseMetricDescription POINT_IN_TIME_METRIC = (NumericalBaseMetricDescription) MetricDescriptionConstants.POINT_IN_TIME_METRIC;
    private final NumericalBaseMetricDescription dataMetric;

    public SlidingWindowRuntimeMeasurementsRecorder(final RuntimeMeasurementModel rmModel,
            final MeasurementSpecification measurementSpecification) {
        super(Objects.requireNonNull(rmModel), Objects.requireNonNull(measurementSpecification));
        this.dataMetric = getDataMetric();
    }

    private NumericalBaseMetricDescription getDataMetric() {
        // find the base matric of the data:
        // any metric that is not point in time, such as state of active resource,
        // or, if all base metrics are point in time, return point in time
        return Arrays
                .stream(MetricDescriptionUtility
                        .toBaseMetricDescriptions(getMeasurementSpecification().getMetricDescription()))
                .filter(m -> !MetricDescriptionUtility.metricDescriptionIdsEqual(m, POINT_IN_TIME_METRIC)).findAny()
                .map(m -> (NumericalBaseMetricDescription) m).orElse(POINT_IN_TIME_METRIC);
        // .orElseThrow(() -> new IllegalArgumentException("Data metric could not be found."));

    }

    @Override
    public void initialize(final IRecorderConfiguration recorderConfiguration) {
    }

    @Override
    public void writeData(final MeasuringValue measurement) {
        newMeasurementAvailable(measurement);

    }

    @Override
    public void flush() {
    }

    @Override
    public void newMeasurementAvailable(final MeasuringValue newMeasurement) {
        if (!Objects.requireNonNull(newMeasurement)
                .isCompatibleWith(getMeasurementSpecification().getMetricDescription())) {
            throw new IllegalArgumentException("Incompatible measurement received!");
        }
        final Measure<Double, Quantity> measure = newMeasurement.getMeasureForMetric(this.dataMetric);
        // forward value (expressed as double in receiving unit!) to RuntimeMeasurementModel
        updateMeasurementValue(measure.doubleValue(measure.getUnit()));
    }

    @Override
    public void preUnregister() {
        detachFromPRM();
    }
}