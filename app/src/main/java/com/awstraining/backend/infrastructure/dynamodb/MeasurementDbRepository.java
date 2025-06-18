package com.awstraining.backend.infrastructure.dynamodb;

import java.util.List;

import com.awstraining.backend.domain.measurement.CouldNotSaveMeasurementException;
import com.awstraining.backend.domain.measurement.UnknownDeviceException;
import com.awstraining.backend.domain.measurement.model.MeasurementDO;
import com.awstraining.backend.domain.measurement.repository.MeasurementRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@RequiredArgsConstructor
@Repository
public class MeasurementDbRepository implements MeasurementRepository {
    private static final Logger LOGGER = LogManager.getLogger(MeasurementDbRepository.class);
    private static final String ERROR_MEASUREMENTS_SAVE_COUNTER = "error_measurements_save_counter";
    private final Counter errorMeasurementSaveCounter;

    @Value("${backend.measurements.ttlInSeconds:2592000}")
    private Long ttlInSeconds;

    private final DynamoDbTable<MeasurementDbEntity> measurementTable;

    @Autowired
    public MeasurementDbRepository(final DynamoDbTable<MeasurementDbEntity> measurementTable, final MeterRegistry meterRegistry) {
        this.measurementTable = measurementTable;
        this.errorMeasurementSaveCounter = Counter.builder(ERROR_MEASUREMENTS_SAVE_COUNTER)
                .description("Number of errors during save of measurements")
                .register(meterRegistry);
    }

    @Override
    public void save(final MeasurementDO measurement) {
        try {
            measurementTable.putItem(MeasurementDbEntityMapper.toEntity(measurement, ttlInSeconds));
        } catch(final DynamoDbException e) {
            LOGGER.error("Could not save measurement", e);
            // Thanks to Micrometer, this metric will be incremented and exported to Prometheus
            // under /actuator/prometheus endpoint of our app.
            errorMeasurementSaveCounter.increment();
            throw new CouldNotSaveMeasurementException();
        }
    }

    @Override
    public List<MeasurementDO> findById(final String deviceId) {
        final QueryConditional queryConditional = QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(deviceId)
                .build());

        // Query all results into a List
        try {
            return measurementTable.query(r -> r.queryConditional(queryConditional))
                    .items()
                    .stream()
                    .map(MeasurementDbEntityMapper::toDomain)
                    .toList();
        } catch(final DynamoDbException e) {
            LOGGER.error("Could not retrieve measurement", e);
            throw new UnknownDeviceException();
        }
    }
}
