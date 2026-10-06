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
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.DetectorReading;
import sms.com.sms.repository.DetectorReadingRepository;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.service.GasDetectorService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final DetectorReadingRepository detectorReadingRepository;

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

   @Operation(summary = "Filter detectors with the full payload required by reports and analytics")
   @GetMapping("/user/filter")
   @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_USER')")
   public ResponseEntity<Map<String, Object>> filterDetectors(
           @RequestParam(required = false) String macAddress,
           @RequestParam(required = false) String location,
           @RequestParam(required = false) Boolean status,
           @RequestParam(required = false) Boolean healthy,
           @RequestParam(required = false) String from,
           @RequestParam(required = false) String to,
           @RequestParam(defaultValue = "0") int page,
           @RequestParam(defaultValue = "50") int size,
           @RequestParam(defaultValue = "macAddress") String sortBy) {
       try {
           List<CropDeceaseDetector> allDetectors = detectorRepository.findAll();

           List<Map<String, Object>> filteredData = allDetectors.stream()
                   .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
                   .map(this::buildDetectorPayload)
                   .sorted((left, right) -> compareBySortField(left, right, sortBy))
                   .skip((long) page * size)
                   .limit(size)
                   .collect(Collectors.toList());

           List<Map<String, Object>> allMatches = allDetectors.stream()
                   .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
                   .map(this::buildDetectorPayload)
                   .collect(Collectors.toList());

           Map<String, Object> response = new LinkedHashMap<>();
           response.put("success", true);
           response.put("page", page);
           response.put("size", size);
           response.put("count", filteredData.size());
           response.put("total", allMatches.size());
           response.put("data", filteredData);
           response.put("report", buildReportSummary(allMatches));
           response.put("analytics", buildAnalyticsSummary(allMatches));
           return ResponseEntity.ok(response);
       } catch (Exception e) {
           log.error("Error filtering detectors", e);
           return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                   .body(createErrorResponse("Failed to filter detector data: " + e.getMessage()));
       }
   }

   @Operation(summary = "Get report data for all detector records")
   @GetMapping("/admin/report")
   @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_USER')")
   public ResponseEntity<Map<String, Object>> getDetectorReport(
           @RequestParam(required = false) String macAddress,
           @RequestParam(required = false) String location,
           @RequestParam(required = false) Boolean status,
           @RequestParam(required = false) Boolean healthy,
           @RequestParam(required = false) String from,
           @RequestParam(required = false) String to) {
       List<CropDeceaseDetector> allDetectors = detectorRepository.findAll();
       List<Map<String, Object>> filtered = allDetectors.stream()
               .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
               .map(this::buildDetectorPayload)
               .collect(Collectors.toList());

       Map<String, Object> response = new LinkedHashMap<>();
       response.put("success", true);
       response.put("count", filtered.size());
       response.put("data", filtered);
       response.put("report", buildReportSummary(filtered));
       response.put("analytics", buildAnalyticsSummary(filtered));
       return ResponseEntity.ok(response);
   }

   @Operation(summary = "Get analytics summary for detectors")
   @GetMapping("/user/analytics")
   @PreAuthorize("hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_USER')")
   public ResponseEntity<Map<String, Object>> getDetectorAnalytics(
           @RequestParam(required = false) String macAddress,
           @RequestParam(required = false) String location,
           @RequestParam(required = false) Boolean status,
           @RequestParam(required = false) Boolean healthy,
           @RequestParam(required = false) String from,
           @RequestParam(required = false) String to) {
       List<CropDeceaseDetector> allDetectors = detectorRepository.findAll();
       List<Map<String, Object>> filtered = allDetectors.stream()
               .filter(detector -> matchesFilter(detector, macAddress, location, status, healthy, from, to))
               .map(this::buildDetectorPayload)
               .collect(Collectors.toList());

       Map<String, Object> response = new LinkedHashMap<>();
       response.put("success", true);
       response.put("count", filtered.size());
       response.put("analytics", buildAnalyticsSummary(filtered));
       response.put("report", buildReportSummary(filtered));
       response.put("data", filtered);
       return ResponseEntity.ok(response);
   }

   private boolean matchesFilter(CropDeceaseDetector detector, String macAddress, String location, Boolean status,
                                Boolean healthy, String from, String to) {
       if (detector == null) {
           return false;
       }
       if (macAddress != null && !macAddress.isBlank() && !macAddress.equalsIgnoreCase(detector.getMacAddress())) {
           return false;
       }
       if (location != null && !location.isBlank() && !location.equalsIgnoreCase(detector.getLocation())) {
           return false;
       }
       if (status != null && !status.equals(detector.getStatus())) {
           return false;
       }

       DetectorReading lastReading = detectorReadingRepository.findTopByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).orElse(null);
       if (healthy != null && lastReading != null && !healthy.equals(Boolean.valueOf(Boolean.TRUE.equals(lastReading.getHealthy())))) {
           return false;
       }
       if (healthy != null && lastReading == null) {
           return false;
       }

       if (from != null && !from.isBlank()) {
           try {
               java.time.Instant fromInstant = java.time.Instant.parse(from);
               if (lastReading == null || lastReading.getReceivedAt() == null || lastReading.getReceivedAt().isBefore(fromInstant)) {
                   return false;
               }
           } catch (java.time.format.DateTimeParseException ignored) {
               return false;
           }
       }
       if (to != null && !to.isBlank()) {
           try {
               java.time.Instant toInstant = java.time.Instant.parse(to);
               if (lastReading == null || lastReading.getReceivedAt() == null || lastReading.getReceivedAt().isAfter(toInstant)) {
                   return false;
               }
           } catch (java.time.format.DateTimeParseException ignored) {
               return false;
           }
       }
       return true;
   }

   private Map<String, Object> buildDetectorPayload(CropDeceaseDetector detector) {
       Map<String, Object> payload = new LinkedHashMap<>();
       payload.put("macAddress", detector.getMacAddress());
       payload.put("status", detector.getStatus());
       payload.put("location", detector.getLocation());
       payload.put("co2", detector.getCo2());
       payload.put("co2Threshold", detector.getCo2Threshold());
       payload.put("wifiSsid", detector.getWifiSsid());
       payload.put("phoneNumbers", detector.getUsers() == null ? new ArrayList<>() : detector.getUsers().stream()
               .map(user -> user.getPhonenumber())
               .filter(phone -> phone != null && !phone.isBlank())
               .collect(Collectors.toList()));

       DetectorReading lastReading = detectorReadingRepository.findTopByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).orElse(null);
       if (lastReading == null) {
           payload.put("lastReading", null);
           payload.put("readingCount", 0);
           payload.put("healthy", null);
           return payload;
       }

       Map<String, Object> reading = new LinkedHashMap<>();
       reading.put("id", lastReading.getId());
       reading.put("temperature", lastReading.getTemperature());
       reading.put("humidity", lastReading.getHumidity());
       reading.put("soil", lastReading.getSoil());
       reading.put("soilMoisture", lastReading.getSoilMoisture());
       reading.put("gas", lastReading.getGas());
       reading.put("co2", lastReading.getCo2());
       reading.put("receivedAt", lastReading.getReceivedAt());
       reading.put("overallStatus", lastReading.getOverallStatus());
       reading.put("temperatureStatus", lastReading.getTemperatureStatus());
       reading.put("humidityStatus", lastReading.getHumidityStatus());
       reading.put("soilMoistureStatus", lastReading.getSoilMoistureStatus());
       reading.put("healthy", lastReading.getHealthy());
       reading.put("message", lastReading.getMessage());
       reading.put("recommendation", lastReading.getRecommendation());
       reading.put("suggestedActions", lastReading.getSuggestedActions());

       payload.put("lastReading", reading);
       payload.put("readingCount", detectorReadingRepository.findByDetectorMacAddressOrderByReceivedAtDesc(detector.getMacAddress()).size());
       payload.put("healthy", lastReading.getHealthy());
       return payload;
   }

   private Map<String, Object> buildReportSummary(List<Map<String, Object>> filtered) {
       Map<String, Object> report = new LinkedHashMap<>();
       report.put("totalRecords", filtered.size());
       report.put("activeDetectors", filtered.stream().filter(entry -> Boolean.TRUE.equals(entry.get("status"))).count());
       report.put("inactiveDetectors", filtered.stream().filter(entry -> Boolean.FALSE.equals(entry.get("status"))).count());
       report.put("healthyDetectors", filtered.stream().filter(entry -> Boolean.TRUE.equals(entry.get("healthy"))).count());
       report.put("unhealthyDetectors", filtered.stream().filter(entry -> Boolean.FALSE.equals(entry.get("healthy"))).count());
       return report;
   }

   private Map<String, Object> buildAnalyticsSummary(List<Map<String, Object>> filtered) {
       Map<String, Object> analytics = new LinkedHashMap<>();
       List<Double> co2Values = filtered.stream()
               .map(entry -> (Map<String, Object>) entry.get("lastReading"))
               .filter(reading -> reading != null)
               .map(reading -> reading.get("co2"))
               .filter(value -> value instanceof Number)
               .map(value -> ((Number) value).doubleValue())
               .collect(Collectors.toList());

       List<Double> humidityValues = filtered.stream()
               .map(entry -> (Map<String, Object>) entry.get("lastReading"))
               .filter(reading -> reading != null)
               .map(reading -> reading.get("humidity"))
               .filter(value -> value instanceof Number)
               .map(value -> ((Number) value).doubleValue())
               .collect(Collectors.toList());

       List<Double> temperatureValues = filtered.stream()
               .map(entry -> (Map<String, Object>) entry.get("lastReading"))
               .filter(reading -> reading != null)
               .map(reading -> reading.get("temperature"))
               .filter(value -> value instanceof Number)
               .map(value -> ((Number) value).doubleValue())
               .collect(Collectors.toList());

       analytics.put("averageCo2", co2Values.isEmpty() ? 0.0 : co2Values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
       analytics.put("averageHumidity", humidityValues.isEmpty() ? 0.0 : humidityValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
       analytics.put("averageTemperature", temperatureValues.isEmpty() ? 0.0 : temperatureValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
       analytics.put("maxCo2", co2Values.isEmpty() ? 0.0 : co2Values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
       analytics.put("locations", filtered.stream()
               .collect(Collectors.groupingBy(entry -> entry.get("location") == null ? "Unknown" : String.valueOf(entry.get("location")), Collectors.counting())));
       return analytics;
   }

   private int compareBySortField(Map<String, Object> left, Map<String, Object> right, String sortBy) {
       Object leftValue = left.get(sortBy == null || sortBy.isBlank() ? "macAddress" : sortBy);
       Object rightValue = right.get(sortBy == null || sortBy.isBlank() ? "macAddress" : sortBy);
       if (leftValue == null && rightValue == null) {
           return 0;
       }
       if (leftValue == null) {
           return 1;
       }
       if (rightValue == null) {
           return -1;
       }
       String leftString = String.valueOf(leftValue);
       String rightString = String.valueOf(rightValue);
       return leftString.compareToIgnoreCase(rightString);
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
            
            CropDeceaseDetector detector = detectorRepository.findById(decodedMac)
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