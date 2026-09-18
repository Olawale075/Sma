package sms.com.sms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sms.com.sms.dto.DetectorDTO;
import sms.com.sms.enums.NotificationPreference;
import sms.com.sms.mapper.DetectorMapper;
import sms.com.sms.model.GasDetector;
import sms.com.sms.model.Users;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.repository.UsersRepository;
import sms.com.sms.exception.ResourceNotFoundException;
import sms.com.sms.exception.DuplicateResourceException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class GasDetectorService {

    private final GasDetectorRepository detectorRepository;
    private final UsersRepository usersRepository;
    private final DetectorMapper detectorMapper;
    private final SmsService smsService;
    private final EmailService emailService;

    private void validateMacAddress(String macAddress) {
        if (macAddress == null || macAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("MAC address cannot be empty");
        }
    }

    public DetectorDTO create(DetectorDTO dto) {
        try {
            log.info("Creating new detector with MAC: {}", dto.getMacAddress());
            
            validateMacAddress(dto.getMacAddress());
            
            if (detectorRepository.existsById(dto.getMacAddress())) {
                log.error("Detector already exists with MAC: {}", dto.getMacAddress());
                throw new DuplicateResourceException("Detector already exists with MAC: " + dto.getMacAddress());
            }

            GasDetector detector = detectorMapper.toEntity(dto);


            if (dto.getPhoneNumbers() != null && !dto.getPhoneNumbers().isEmpty()) {
                Set<Users> users = dto.getPhoneNumbers().stream()
                        .map(phone -> usersRepository.findByPhonenumber(phone)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + phone)))
                        .collect(Collectors.toSet());
                detector.setUsers(users);
            }

            GasDetector saved = detectorRepository.save(detector);
            log.info(" Detector created successfully: {}", saved.getMacAddress());
            return detectorMapper.toDto(saved);
            
        } catch (DuplicateResourceException | ResourceNotFoundException e) {
            log.error("Error creating detector: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while creating detector", e);
            throw new RuntimeException("Failed to create detector: " + e.getMessage(), e);
        }
    }


    @Transactional(readOnly = true)
    public List<DetectorDTO> findAll() {
        try {
            log.info("Fetching all detectors");
            return detectorRepository.findAll().stream()
                    .map(detectorMapper::toDto)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error fetching all detectors", e);
            throw new RuntimeException("Failed to fetch detectors: " + e.getMessage(), e);
        }
    }


    @Transactional(readOnly = true)
    public DetectorDTO findByMac(String mac) {
        try {
            log.info("Fetching detector by MAC: {}", mac);
            validateMacAddress(mac);
            
            return detectorRepository.findById(mac)
                    .map(detectorMapper::toDto)
                    .orElseThrow(() -> {
                        log.warn(" Detector not found: {}", mac);
                        return new ResourceNotFoundException("Detector not found with MAC: " + mac);
                    });
                    
        } catch (ResourceNotFoundException e) {
            log.error("Error finding detector by MAC: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid MAC address: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while finding detector by MAC", e);
            throw new RuntimeException("Failed to find detector: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<DetectorDTO> getAllPaged(Pageable pageable) {
        try {
            log.info("Fetching detectors with pagination");
            if (pageable == null) {
                throw new IllegalArgumentException("Pageable cannot be null");
            }
            return detectorRepository.findAll(pageable)
                    .map(detectorMapper::toDto);
                    
        } catch (IllegalArgumentException e) {
            log.error("Invalid pageable: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching paginated detectors", e);
            throw new RuntimeException("Failed to fetch detectors with pagination: " + e.getMessage(), e);
        }
    }
public DetectorDTO updateDetectorConfiguration(String macAddress, DetectorDTO updatedData) {
    try {
        log.info(" Updating detector configuration: {}", macAddress);
        validateMacAddress(macAddress);

        if (updatedData == null) {
            throw new IllegalArgumentException("Updated data cannot be null");
        }

        GasDetector detector = detectorRepository.findById(macAddress)
                .orElseThrow(() -> {
                    log.error(" Detector not found: {}", macAddress);
                    return new ResourceNotFoundException("Detector not found with MAC: " + macAddress);
                });


        if (updatedData.getCo2Threshold() != null && updatedData.getCo2Threshold() > 0) {
            
            detector.setCo2Threshold(updatedData.getCo2Threshold());
            log.info("Updated CO2 threshold: {}", updatedData.getCo2Threshold());
        }
        

        if (updatedData.getWifiSsid() != null && !updatedData.getWifiSsid().trim().isEmpty()) {
            detector.setWifiSsid(updatedData.getWifiSsid().trim());
            log.info("Updated WiFi SSID: {}", updatedData.getWifiSsid());
        }
        
       
        if (updatedData.getWifiPassword() != null && !updatedData.getWifiPassword().trim().isEmpty()) {
            detector.setWifiPassword(updatedData.getWifiPassword().trim());
            log.info("Updated WiFi Password");
        }

        GasDetector saved = detectorRepository.save(detector);
        log.info(" Detector configuration updated successfully");
        
        return detectorMapper.toDto(saved);
        
    } catch (ResourceNotFoundException | IllegalArgumentException e) {
        log.error("Error updating detector configuration: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error while updating detector configuration", e);
        throw new RuntimeException("Failed to update detector configuration: " + e.getMessage(), e);
    }
}
    // ==================================================
    // UPDATE (From Arduino - sensor data only)
    // ==================================================
   public DetectorDTO updateDetectorByMac(String macAddress, DetectorDTO updatedData) {
    try {
        log.info("📥 Updating detector: {}", macAddress);
        validateMacAddress(macAddress);

        if (updatedData == null) {
            throw new IllegalArgumentException("Updated data cannot be null");
        }

        GasDetector detector = detectorRepository.findById(macAddress)
                .orElseThrow(() -> {
                    log.error(" Detector not found: {}", macAddress);
                    return new ResourceNotFoundException("Detector not found with MAC: " + macAddress);
                });

        // Update sensor readings (Temperature, Humidity, CO2, Status)
        if (updatedData.getTemperature() != null) {
            detector.setTemperature(updatedData.getTemperature());
            log.debug("Updated temperature: {}", updatedData.getTemperature());
        }
        
        if (updatedData.getHumidity() != null) {
            detector.setHumidity(updatedData.getHumidity());
            log.debug("Updated humidity: {}", updatedData.getHumidity());
        }
        
        if (updatedData.getCo2() != null) {
            detector.setCo2(updatedData.getCo2());
            log.debug("Updated CO2: {}", updatedData.getCo2());
        }
        
        if (updatedData.getStatus() != null) {
            detector.setStatus(updatedData.getStatus());
            log.debug("Updated status: {}", updatedData.getStatus());
        }

        // Update configuration (Threshold, WiFi - only if provided)
        if (updatedData.getCo2Threshold() != null && updatedData.getCo2Threshold() > 0) {
            // validateCo2Threshold(updatedData.getCo2Threshold());
            detector.setCo2Threshold(updatedData.getCo2Threshold());
            log.info("Updated CO2 threshold: {}", updatedData.getCo2Threshold());
        }
        
        if (updatedData.getWifiSsid() != null && !updatedData.getWifiSsid().trim().isEmpty()) {
            detector.setWifiSsid(updatedData.getWifiSsid().trim());
            log.info("Updated WiFi SSID: {}", updatedData.getWifiSsid());
        }
        
        if (updatedData.getWifiPassword() != null && !updatedData.getWifiPassword().trim().isEmpty()) {
            detector.setWifiPassword(updatedData.getWifiPassword().trim());
            log.info("Updated WiFi Password");
        }

        GasDetector saved = detectorRepository.save(detector);
        log.info(" Detector updated successfully");
        
        // Send hazard notification if status is true (DANGER)
        if (detector.getStatus() != null && detector.getStatus()) {
            String alertMessage = String.format(
                    " GAS ALERT!\nTemp: %.1f°C\nHumidity: %.1f%%\nCO2: %.1f ppm",
                    detector.getTemperature(),
                    detector.getHumidity(),
                    detector.getCo2()
            );
            try {
                notifyUsersByDetector(macAddress, alertMessage);
            } catch (Exception e) {
                log.error("Failed to send alert notification", e);
            }
        }
        
        return detectorMapper.toDto(saved);
        
    } catch (ResourceNotFoundException | IllegalArgumentException e) {
        log.error("Error updating detector: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error while updating detector", e);
        throw new RuntimeException("Failed to update detector: " + e.getMessage(), e);
    }
}
    // ==================================================
    // UPDATE (From Admin - full update via DTO)
    // ==================================================
    public DetectorDTO update(String mac, DetectorDTO dto) {
        try {
            log.info("Updating detector via admin panel: {}", mac);
            validateMacAddress(mac);

            if (dto == null) {
                throw new IllegalArgumentException("Detector DTO cannot be null");
            }

            GasDetector existing = detectorRepository.findById(mac)
                    .orElseThrow(() -> {
                        log.error(" Detector not found: {}", mac);
                        return new ResourceNotFoundException("Detector not found with MAC: " + mac);
                    });

            // === Sensor values ===
            if (dto.getTemperature() != null) {
                existing.setTemperature(dto.getTemperature());
            }
            if (dto.getHumidity() != null) {
                existing.setHumidity(dto.getHumidity());
            }
            if (dto.getCo2() != null) {
                existing.setCo2(dto.getCo2());
            }
            if (dto.getStatus() != null) {
                existing.setStatus(dto.getStatus());
            }

            // === Update linked users ===
            if (dto.getPhoneNumbers() != null && !dto.getPhoneNumbers().isEmpty()) {
                Set<Users> users = dto.getPhoneNumbers().stream()
                        .map(phone -> usersRepository.findByPhonenumber(phone)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + phone)))
                        .collect(Collectors.toSet());
                existing.setUsers(users);
                log.info("Updated linked users: {}", dto.getPhoneNumbers().size());
            }

            GasDetector saved = detectorRepository.save(existing);
            log.info(" Detector updated successfully via admin");
            return detectorMapper.toDto(saved);
            
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            log.error("Error updating detector: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while updating detector", e);
            throw new RuntimeException("Failed to update detector: " + e.getMessage(), e);
        }
    }

    // ==================================================
    // DELETE
    // ==================================================
    public void delete(String mac) {
        try {
            log.info("Deleting detector: {}", mac);
            validateMacAddress(mac);
            
            if (!detectorRepository.existsById(mac)) {
                log.error("Detector not found for deletion: {}", mac);
                throw new ResourceNotFoundException("Detector not found with MAC: " + mac);
            }
            
            detectorRepository.deleteById(mac);
            log.info(" Detector deleted: {}", mac);
            
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            log.error("Error deleting detector: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while deleting detector", e);
            throw new RuntimeException("Failed to delete detector: " + e.getMessage(), e);
        }
    }

    // ==================================================
    // ASSIGN DETECTOR TO USER
    // ==================================================
    public String assignDetectorToUser(String phoneNumber, String macAddress) {
        try {
            log.info("Assigning detector {} to user {}", macAddress, phoneNumber);
            
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Phone number cannot be empty");
            }
            validateMacAddress(macAddress);
            
            String normalizedNumber = normalizePhoneNumber(phoneNumber);

            Users user = usersRepository.findByPhonenumber(normalizedNumber)
                    .orElseThrow(() -> {
                        log.error(" User not found: {}", normalizedNumber);
                        return new ResourceNotFoundException("User not found with phone: " + normalizedNumber);
                    });

            GasDetector gasDetector = detectorRepository.findById(macAddress)
                    .orElseThrow(() -> {
                        log.error(" Detector not found: {}", macAddress);
                        return new ResourceNotFoundException("Detector not found with MAC: " + macAddress);
                    });

            if (user.getGasDetectors().contains(gasDetector)) {
                log.warn(" Detector already assigned to user: {}", normalizedNumber);
                return "Detector already assigned to this user";
            }

            user.addGasDetector(gasDetector);
            usersRepository.save(user);
            
            log.info(" Detector assigned successfully");
            return "Successfully linked detector to user";
            
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            log.error("Error assigning detector to user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while assigning detector", e);
            throw new RuntimeException("Failed to assign detector: " + e.getMessage(), e);
        }
    }

    // ==================================================
    // NOTIFICATIONS
    // ==================================================
    public String notifyUsersByDetector(String macAddress, String message) {
        try {
            log.info("📨 Sending notifications for detector: {}", macAddress);
            validateMacAddress(macAddress);

            if (message == null || message.trim().isEmpty()) {
                throw new IllegalArgumentException("Message cannot be empty");
            }
            
            GasDetector detector = detectorRepository.findById(macAddress)
                    .orElseThrow(() -> {
                        log.error(" Detector not found: {}", macAddress);
                        return new ResourceNotFoundException("Detector not found with MAC: " + macAddress);
                    });

            if (detector.getUsers() == null || detector.getUsers().isEmpty()) {
                log.warn(" No users linked to detector: {}", macAddress);
                return "No users linked to this detector";
            }

            int notificationsSent = 0;
            
            for (Users user : detector.getUsers()) {
                try {
                    NotificationPreference preference = user.getNotificationPreference();
                    
                    if (preference == null) {
                        preference = NotificationPreference.SMS;
                    }

                    switch (preference) {
                        case EMAIL:
                            emailService.sendEmail(
                                    user.getEmail(),
                                    " Gas Detector Alert",
                                    message
                            );
                            log.info("📧 Email sent to: {}", user.getEmail());
                            break;
                            
                        case SMS:
                        case MOBILE_APP:
                            smsService.sendSms(user.getPhonenumber(), message);
                            log.info("📱 SMS sent to: {}", user.getPhonenumber());
                            break;
                    }
                    notificationsSent++;
                    
                } catch (Exception e) {
                    log.error(" Failed to send notification to user: {}", user.getPhonenumber(), e);
                }
            }

            String result = String.format(" Notifications sent to %d users", notificationsSent);
            log.info(result);
            return result;
            
        } catch (ResourceNotFoundException | IllegalArgumentException e) {
            log.error("Error notifying users: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while notifying users", e);
            throw new RuntimeException("Failed to send notifications: " + e.getMessage(), e);
        }
    }

    // ==================================================
    // CHECK DETECTOR EXISTS
    // ==================================================
    @Transactional(readOnly = true)
    public boolean detectorExists(String macAddress) {
        try {
            validateMacAddress(macAddress);
            return detectorRepository.existsById(macAddress);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid MAC address: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error checking if detector exists", e);
            throw new RuntimeException("Failed to check detector existence: " + e.getMessage(), e);
        }
    }

    // ==================================================
    // HELPER METHOD - PHONE NUMBER NORMALIZATION
    // ==================================================
    private String normalizePhoneNumber(String phoneNumber) {
        try {
            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                throw new IllegalArgumentException("Phone number cannot be empty");
            }
            
            String trimmed = phoneNumber.trim();
            return trimmed.startsWith("234") ? trimmed : "234" + trimmed.replaceFirst("^0", "");
            
        } catch (IllegalArgumentException e) {
            log.error("Error normalizing phone number: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while normalizing phone number", e);
            throw new RuntimeException("Failed to normalize phone number: " + e.getMessage(), e);
        }
    }
}