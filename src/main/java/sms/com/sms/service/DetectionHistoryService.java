package sms.com.sms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sms.com.sms.ObjectDetectionService;
import sms.com.sms.model.DetectionHistory;
import sms.com.sms.repository.DetectionHistoryRepository;
import sms.com.sms.repository.UsersRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DetectionHistoryService {

    private final DetectionHistoryRepository detectionHistoryRepository;
    private final UsersRepository usersRepository;
    private final ObjectMapper objectMapper;

    public DetectionHistoryService(DetectionHistoryRepository detectionHistoryRepository,
                                   UsersRepository usersRepository,
                                   ObjectMapper objectMapper) {
        this.detectionHistoryRepository = detectionHistoryRepository;
        this.usersRepository = usersRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public boolean chargeAndSave(String userPhone, String deviceId, String location, String focus,
                                 String language, Double temperature, Double humidity,
                                 List<ObjectDetectionService.DetectionResult> results) {
        if (usersRepository.deductAiTokens(userPhone, BigDecimal.TEN) == 0) {
            return false;
        }

        ObjectDetectionService.DetectionResult top = results.get(0);
        DetectionHistory history = new DetectionHistory();
        history.setUserPhone(userPhone);
        history.setDeviceId(deviceId);
        history.setLocation(location);
        history.setFocus(focus);
        history.setLanguage(language);
        history.setTemperature(temperature);
        history.setHumidity(humidity);
        history.setSummary(top.summary);
        history.setClassName(top.className);
        history.setDomain(top.domain == null ? null : top.domain.name());
        history.setConfidence(top.probability);
        history.setHealthStatus(top.healthStatus);
        history.setSeverity(top.severity == null ? null : top.severity.name());
        history.setConditionName(top.conditionName);
        history.setTreatment(top.treatment);
        history.setWhenToAct(top.whenToAct);
        try {
            history.setResultsJson(objectMapper.writeValueAsString(results));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize detection results for history", e);
        }
        detectionHistoryRepository.save(history);
        return true;
    }
}
