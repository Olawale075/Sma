package sms.com.sms.mapper;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import sms.com.sms.dto.DetectorDTO;
import sms.com.sms.model.GasDetector;
import sms.com.sms.model.Users;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DetectorMapper {

    /**
     * Convert Entity to DTO
     * Maps all detector fields including sensor data, configuration, and linked users
     */
    public DetectorDTO toDto(GasDetector detector) {
        if (detector == null) {
            log.warn("Attempting to map null GasDetector entity");
            return null;
        }

        try {
            log.debug("Mapping GasDetector to DetectorDTO: {}", detector.getMacAddress());
            
            return DetectorDTO.builder()
                    .macAddress(detector.getMacAddress())
                    .status(detector.getStatus())
                    .temperature(detector.getTemperature())
                    .humidity(detector.getHumidity())
                    .co2(detector.getCo2())
                    .co2Threshold(detector.getCo2Threshold())
                    .wifiSsid(detector.getWifiSsid())
                    .wifiPassword(detector.getWifiPassword())
                    .location(detector.getLocation())
                    .phoneNumbers(extractPhoneNumbers(detector.getUsers()))
                    .build();
                    
        } catch (Exception e) {
            log.error("Error mapping GasDetector to DetectorDTO", e);
            throw new RuntimeException("Failed to map detector to DTO: " + e.getMessage(), e);
        }
    }

    /**
     * Convert DTO to Entity
     * Maps all detector fields for entity creation
     */
    public GasDetector toEntity(DetectorDTO dto) {
        if (dto == null) {
            log.warn("Attempting to map null DetectorDTO");
            return null;
        }

        try {
            log.debug("Mapping DetectorDTO to GasDetector entity: {}", dto.getMacAddress());
            
            return GasDetector.builder()
                    .macAddress(dto.getMacAddress())
                    .status(dto.getStatus())
                    .temperature(dto.getTemperature())
                    .humidity(dto.getHumidity())
                    .co2(dto.getCo2())
                    .co2Threshold(dto.getCo2Threshold())
                    .wifiSsid(dto.getWifiSsid())
                    .wifiPassword(dto.getWifiPassword())
                    .location(dto.getLocation())
                    .users(new java.util.HashSet<>())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error mapping DetectorDTO to GasDetector entity", e);
            throw new RuntimeException("Failed to map DTO to entity: " + e.getMessage(), e);
        }
    }

    /**
     * Extract phone numbers from Users set
     * Filters out null/empty values and returns null if no users exist
     */
    private Set<String> extractPhoneNumbers(Set<Users> users) {
        try {
            if (users == null || users.isEmpty()) {
                log.debug("No users linked to detector");
                return null;
            }
            
            Set<String> phoneNumbers = users.stream()
                    .map(Users::getPhonenumber)
                    .filter(phone -> phone != null && !phone.isEmpty())
                    .collect(Collectors.toSet());
            
            log.debug("Extracted {} phone numbers from users", phoneNumbers.size());
            return phoneNumbers.isEmpty() ? null : phoneNumbers;
            
        } catch (Exception e) {
            log.error("Error extracting phone numbers from users", e);
            throw new RuntimeException("Failed to extract phone numbers: " + e.getMessage(), e);
        }
    }

    /**
     * Update entity from DTO (useful for partial updates)
     * Only updates non-null fields
     */
    public void updateEntityFromDto(DetectorDTO dto, GasDetector entity) {
        if (dto == null || entity == null) {
            log.warn("Cannot update entity - DTO or Entity is null");
            return;
        }

        try {
            log.debug("Updating GasDetector entity from DetectorDTO");

            if (dto.getTemperature() != null) entity.setTemperature(dto.getTemperature());
            if (dto.getHumidity() != null) entity.setHumidity(dto.getHumidity());
            if (dto.getCo2() != null) entity.setCo2(dto.getCo2());
            if (dto.getStatus() != null) entity.setStatus(dto.getStatus());
            if (dto.getCo2Threshold() != null) entity.setCo2Threshold(dto.getCo2Threshold());
            
            if (dto.getWifiSsid() != null && !dto.getWifiSsid().trim().isEmpty()) {
                entity.setWifiSsid(dto.getWifiSsid().trim());
            }
            if (dto.getWifiPassword() != null && !dto.getWifiPassword().trim().isEmpty()) {
                entity.setWifiPassword(dto.getWifiPassword().trim());
            }

            log.debug("✅ Successfully updated GasDetector entity from DetectorDTO");
            
        } catch (Exception e) {
            log.error("Error updating entity from DTO", e);
            throw new RuntimeException("Failed to update entity from DTO: " + e.getMessage(), e);
        }
    }
}