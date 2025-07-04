package com.awstraining.backend.application.measurement.controller;

import static java.lang.System.currentTimeMillis;

import java.util.List;

import com.awstraining.backend.api.rest.v1.DeviceIdApi;
import com.awstraining.backend.api.rest.v1.model.Measurement;
import com.awstraining.backend.api.rest.v1.model.Measurements;
import com.awstraining.backend.domain.measurement.model.MeasurementDO;
import com.awstraining.backend.domain.measurement.service.MeasurementService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("device/v1")
class MeasurementController implements DeviceIdApi {
    private static final Logger LOGGER = LogManager.getLogger(MeasurementController.class);

    private final MeasurementService service;

    @Autowired
    public MeasurementController(final MeasurementService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Measurement> publishMeasurements(final String deviceId, final Measurement measurement) {
        LOGGER.info("Publishing measurement for device '{}'", deviceId);
        final MeasurementDO measurementDO = fromRequest(deviceId, measurement);
        service.saveMeasurement(measurementDO);
        return ResponseEntity.ok(measurement);
    }
    @Override
    public ResponseEntity<Measurements> retrieveMeasurements(final String deviceId) {
        LOGGER.info("Retrieving all measurements for device '{}'", deviceId);
        final List<Measurement> measurements = service.getMeasurements(deviceId)
                .stream()
                .map(this::toResponse)
                .toList();
        final Measurements measurementsResult = new Measurements();
        measurementsResult.measurements(measurements);
        return ResponseEntity.ok(measurementsResult);
    }

    private Measurement toResponse(final MeasurementDO measurementDO) {
        final Measurement measurement = new Measurement();
        measurement.setTimestamp(measurementDO.getCreationTime());
        measurement.setType(measurementDO.getType());
        measurement.setValue(measurementDO.getValue());
        return measurement;
    }

    private MeasurementDO fromRequest(final String deviceId, final Measurement measurement) {
        final MeasurementDO measurementDO = new MeasurementDO();
        measurementDO.setDeviceId(deviceId);
        measurementDO.setType(measurement.getType());
        measurementDO.setValue(measurement.getValue());
        final Long creationTime = measurement.getTimestamp();
        measurementDO.setCreationTime(creationTime == null ? currentTimeMillis() : creationTime);
        return measurementDO;
    }
}
