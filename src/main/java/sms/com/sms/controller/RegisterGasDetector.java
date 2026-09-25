package sms.com.sms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import sms.com.sms.dto.DetectorDTO;
import sms.com.sms.exception.ResourceNotFoundException;
import sms.com.sms.model.GasDetector;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.service.GasDetectorService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gas-detectors")
@CrossOrigin("*")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class RegisterGasDetector {

    private final GasDetectorService gasDetectorService;
    private final GasDetectorRepository detectorRepository;

    // ==================================================
    // REGISTER
    // ==================================================
    @Operation(summary = "Register a new gas detector")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Detector registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/admin/register")
    public ResponseEntity<Map<String, Object>> registerDetector(@RequestBody DetectorDTO dto) {
        try {
            log.info("Registering new detector with MAC: {}", dto.getMacAddress());
            
            if (dto == null || dto.getMacAddress() == null || dto.getMacAddress().isEmpty()) {
                log.warn("Invalid detector DTO: MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            DetectorDTO saved = gasDetectorService.create(dto);
            log.info(" Detector registered successfully: {}", saved.getMacAddress());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(createSuccessResponse("Detector registered successfully", saved));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during registration: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during detector registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to register detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // ASSIGN DETECTOR TO USER
    // ==================================================
    @Operation(summary = "Assign detector to user by phone number")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector assigned successfully"),
            @ApiResponse(responseCode = "404", description = "Detector or user not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/user/assign")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> assignDetector(
            @RequestParam String phonenumber,
            @RequestParam String macAddress) {
        try {
            log.info("Assigning detector {} to user {}", macAddress, phonenumber);

            if (phonenumber == null || phonenumber.trim().isEmpty()) {
                log.warn("Phone number is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Phone number cannot be empty"));
            }

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            String result = gasDetectorService.assignDetectorToUser(phonenumber, macAddress);
            log.info(" Detector assigned successfully");
            return ResponseEntity.ok(createSuccessResponse(result, null));

        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found during assignment: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during assignment: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during detector assignment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to assign detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // LINK DETECTOR TO AUTHENTICATED USER
    // ==================================================
    @Operation(summary = "Link detector to currently authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector linked successfully"),
            @ApiResponse(responseCode = "404", description = "Detector or user not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/user/link")
    @PreAuthorize("hasAuthority('ROLE_USER') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> linkDetectorToAuthenticatedUser(
            @RequestParam String macAddress,
            Authentication authentication) {
        try {
            String phonenumber = authentication.getName();
            log.info("Linking detector {} to authenticated user {}", macAddress, phonenumber);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest().body(createErrorResponse("MAC address cannot be empty"));
            }

            String result = gasDetectorService.assignDetectorToUser(phonenumber, macAddress);
            return ResponseEntity.ok(createSuccessResponse(result, null));

        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found during linking: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during linking: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during detector linking", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(createErrorResponse("Failed to link detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // GET BY MAC ADDRESS
    // ==================================================
    @Operation(summary = "Get gas detector by MAC address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector found"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid MAC address"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/user/getDetector")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_USER')")
    public ResponseEntity<Map<String, Object>> getDetector(@RequestParam String macAddress) {
        try {
            log.info("Fetching detector by MAC: {}", macAddress);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            DetectorDTO detector = gasDetectorService.findByMac(macAddress);
            log.info(" Detector found: {}", macAddress);
            return ResponseEntity.ok(createSuccessResponse("Detector retrieved successfully", detector));

        } catch (ResourceNotFoundException e) {
            log.warn("Detector not found: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while fetching detector", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // GET ALL (PAGINATED & SORTED)
    // ==================================================
    @Operation(summary = "Get all gas detectors (paginated and sorted)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/admin/all")
    public ResponseEntity<Map<String, Object>> getAllGasDetectors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "macAddress") String sortBy,
            @RequestParam(defaultValue = "asc") String order) {
        try {
            log.info("Fetching all detectors - page: {}, size: {}, sortBy: {}, order: {}", 
                    page, size, sortBy, order);

            if (page < 0) {
                log.warn("Invalid page number: {}", page);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Page number cannot be negative"));
            }

            if (size <= 0 || size > 100) {
                log.warn("Invalid page size: {}", size);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Page size must be between 1 and 100"));
            }

            Sort sort = order.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<DetectorDTO> pagedResult = gasDetectorService.getAllPaged(pageable);

            log.info(" Retrieved {} detectors", pagedResult.getNumberOfElements());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Detectors retrieved successfully");
            response.put("totalElements", pagedResult.getTotalElements());
            response.put("totalPages", pagedResult.getTotalPages());
            response.put("currentPage", page);
            response.put("data", pagedResult.getContent());
            
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Invalid parameters: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while fetching all detectors", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to fetch detectors: " + e.getMessage()));
        }
    }
@PutMapping("/user/configure/{macAddress}")
@PreAuthorize("permitAll()")
public ResponseEntity<Map<String, Object>> configureDetector(
        @PathVariable String macAddress,
        @Valid @RequestBody DetectorDTO detectorDTO) {
    try {
        log.info("Configuring detector: {}", macAddress);
        
        if (macAddress == null || macAddress.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("MAC address cannot be empty"));
        }

        String decodedMac = macAddress.replace("%3A", ":");
        
        DetectorDTO updated = gasDetectorService.updateDetectorConfiguration(decodedMac, detectorDTO);
        log.info(" Configuration updated successfully");
        
        return ResponseEntity.ok(createSuccessResponse("Detector configuration updated", updated));
        
    } catch (ResourceNotFoundException e) {
        log.warn("Detector not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(e.getMessage()));
    } catch (IllegalArgumentException e) {
        log.warn("Validation error: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(createErrorResponse("Validation error: " + e.getMessage()));
    } catch (Exception e) {
        log.error("Error configuring detector", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to configure detector: " + e.getMessage()));
    }
}

    // ==================================================
    // UPDATE (From Admin Panel)
    // ==================================================
    @Operation(summary = "Update gas detector by MAC address (Admin)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector updated successfully"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/device/update/{macAddress}")
    public ResponseEntity<Map<String, Object>> updateDetectorAdmin(
            @PathVariable String macAddress,
            @RequestBody DetectorDTO dto) {
        try {
            log.info("Updating detector (admin panel): {}", macAddress);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            if (dto == null) {
                log.warn("Detector DTO is null");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Detector data cannot be empty"));
            }

            DetectorDTO updated = gasDetectorService.update(macAddress, dto);
            log.info(" Detector updated successfully: {}", macAddress);
            return ResponseEntity.ok(createSuccessResponse("Detector updated successfully", updated));

        } catch (ResourceNotFoundException e) {
            log.warn("Detector not found for update: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during update: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while updating detector", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to update detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // UPDATE (From Device/Arduino)
    // ==================================================
    @Operation(summary = "User Update gas detector sensor data (Device)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector updated successfully"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })

   @PutMapping("/user/update/{macAddress}")
@PreAuthorize("permitAll()")
public ResponseEntity<Map<String, Object>> updateDetectorDevice(
        @PathVariable String macAddress,
        @Valid @RequestBody DetectorDTO updatedData) {
    try {
        log.info("Updating detector (device): {}", macAddress);

        if (macAddress == null || macAddress.trim().isEmpty()) {
            log.warn("MAC address is empty");
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("MAC address cannot be empty"));
        }

        if (updatedData == null) {
            log.warn("Updated data is null");
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Detector data cannot be empty"));
        }

        // Decode MAC address if URL encoded (F1%3AEE%3A34%3A23%3A48%3AC8 -> F1:EE:34:23:48:C8)
        String decodedMac = macAddress.replace("%3A", ":");
        log.debug("Decoded MAC address: {}", decodedMac);

        DetectorDTO updated = gasDetectorService.updateDetectorByMac(decodedMac, updatedData);
        log.info(" Detector updated successfully by device: {}", decodedMac);
        
        return ResponseEntity.ok(createSuccessResponse("Detector updated successfully", updated));

    } catch (ResourceNotFoundException e) {
        log.warn("Detector not found for device update: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(e.getMessage()));

    } catch (IllegalArgumentException e) {
        log.warn("Validation error during device update: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(createErrorResponse("Validation error: " + e.getMessage()));

    } catch (Exception e) {
        log.error("Unexpected error while updating detector by device", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse("Failed to update detector: " + e.getMessage()));
    }
}
    // ==================================================
    // DELETE
    // ==================================================
    @Operation(summary = "Delete gas detector by MAC address")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Detector deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid MAC address"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/admin/delete/{macAddress}")
    public ResponseEntity<Map<String, Object>> deleteDetector(@PathVariable String macAddress) {
        try {
            log.info("Deleting detector: {}", macAddress);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            gasDetectorService.delete(macAddress);
            log.info(" Detector deleted successfully: {}", macAddress);
            return ResponseEntity.ok(createSuccessResponse("Detector deleted successfully", null));

        } catch (ResourceNotFoundException e) {
            log.warn("Detector not found for deletion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during deletion: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while deleting detector", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to delete detector: " + e.getMessage()));
        }
    }

    // ==================================================
    // SEND NOTIFICATION
    // ==================================================
    @Operation(summary = "Send notification to users linked to a detector")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notifications sent successfully"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/users/{macAddress}/notify")
    public ResponseEntity<Map<String, Object>> notifyUsers(
            @PathVariable String macAddress,
            @RequestBody Map<String, String> requestBody) {
        try {
            log.info("Sending notifications for detector: {}", macAddress);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            if (requestBody == null || !requestBody.containsKey("message")) {
                log.warn("Message is missing from request");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Message is required"));
            }

            String message = requestBody.get("message");

            if (message == null || message.trim().isEmpty()) {
                log.warn("Message is blank");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("Message cannot be empty or blank"));
            }

            String result = gasDetectorService.notifyUsersByDetector(macAddress, message);
            log.info(" Notifications sent successfully");
            return ResponseEntity.ok(createSuccessResponse(result, null));

        } catch (ResourceNotFoundException e) {
            log.warn("Detector not found for notification: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (IllegalArgumentException e) {
            log.warn("Validation error during notification: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("Validation error: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error while sending notifications", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to send notifications: " + e.getMessage()));
        }
    }

    // ==================================================
    // PROVISION WIFI
    // ==================================================
    @Operation(summary = "Get WiFi configuration for device provisioning")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "WiFi configuration retrieved"),
            @ApiResponse(responseCode = "404", description = "Detector not found"),
            @ApiResponse(responseCode = "400", description = "Invalid MAC address"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/users/provision/{macAddress}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> provisionWifi(@PathVariable String macAddress) {
        try {
            log.info("Provisioning WiFi for detector: {}", macAddress);

            if (macAddress == null || macAddress.trim().isEmpty()) {
                log.warn("MAC address is empty");
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("MAC address cannot be empty"));
            }

            String decodedMac = macAddress.replace("%3A", ":");
            
            GasDetector detector = detectorRepository.findById(decodedMac)
                    .orElseThrow(() -> {
                        log.error("Detector not found for provisioning: {}", decodedMac);
                        return new ResourceNotFoundException("Detector not found with MAC: " + decodedMac);
                    });

            if (detector.getWifiSsid() == null || detector.getWifiSsid().isEmpty()) {
                log.warn("WiFi SSID not configured for detector: {}", decodedMac);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("WiFi SSID not configured"));
            }

            if (detector.getWifiPassword() == null || detector.getWifiPassword().isEmpty()) {
                log.warn("WiFi password not configured for detector: {}", decodedMac);
                return ResponseEntity.badRequest()
                        .body(createErrorResponse("WiFi password not configured"));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "WiFi configuration retrieved successfully");
            response.put("ssid", detector.getWifiSsid());
            response.put("password", detector.getWifiPassword());

            log.info(" WiFi configuration retrieved successfully");
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException e) {
            log.warn("Resource not found during WiFi provisioning: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(createErrorResponse(e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during WiFi provisioning", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to provision WiFi: " + e.getMessage()));
        }
    }

    // ==================================================
    // HELPER METHODS
    // ==================================================
    private Map<String, Object> createSuccessResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}