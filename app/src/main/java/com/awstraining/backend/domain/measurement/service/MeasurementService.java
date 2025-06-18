package com.awstraining.backend.domain.measurement.service;

import java.util.List;

import com.awstraining.backend.domain.measurement.CouldNotSaveMeasurementException;
import com.awstraining.backend.domain.measurement.model.MeasurementDO;
import com.awstraining.backend.domain.measurement.repository.MeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MeasurementService {
    private final MeasurementRepository repository;

    public void saveMeasurement(final MeasurementDO measurementDO) throws CouldNotSaveMeasurementException {
        repository.save(measurementDO);
    }

    public List<MeasurementDO> getMeasurements(final String deviceId) {
        return repository.findById(deviceId);
    }
}
