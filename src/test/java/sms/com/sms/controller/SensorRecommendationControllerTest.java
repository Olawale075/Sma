package sms.com.sms.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import sms.com.sms.dto.SensorReadingRequest;
import sms.com.sms.enums.Language;
import sms.com.sms.model.CropDeceaseDetector;
import sms.com.sms.model.DetectorReading;
import sms.com.sms.model.Users;
import sms.com.sms.repository.DetectorReadingRepository;
import sms.com.sms.repository.GasDetectorRepository;
import sms.com.sms.repository.UsersRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensorRecommendationControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesRequestLanguageBeforeDetectorLanguageWhenNoUserIsAuthenticated() {
        DetectorReadingRepository readingRepository = mock(DetectorReadingRepository.class);
        GasDetectorRepository gasDetectorRepository = mock(GasDetectorRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        SensorRecommendationController controller = new SensorRecommendationController(
                readingRepository, gasDetectorRepository, usersRepository);
        CropDeceaseDetector detector = CropDeceaseDetector.builder().macAddress("device-1").build();
        Users linkedUser = new Users();
        linkedUser.setPreferredLanguage(Language.YO);

        when(gasDetectorRepository.findByMacAddress("device-1")).thenReturn(detector);
        when(usersRepository.findByCropDeceaseDetectors_MacAddress("device-1"))
                .thenReturn(List.of(linkedUser));
        when(readingRepository.save(any(DetectorReading.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.getRecommendation(
                new SensorReadingRequest(22.0, 50.0, 40.0, Language.FR), null, "device-1");

        assertEquals("fr", response.getBody().get("language"));
        assertEquals("L’environnement respecte les plages idéales.", response.getBody().get("message"));
    }

    @Test
    void usesAuthenticatedUsersSavedLanguageBeforeRequestLanguage() {
        DetectorReadingRepository readingRepository = mock(DetectorReadingRepository.class);
        GasDetectorRepository gasDetectorRepository = mock(GasDetectorRepository.class);
        UsersRepository usersRepository = mock(UsersRepository.class);
        SensorRecommendationController controller = new SensorRecommendationController(
                readingRepository, gasDetectorRepository, usersRepository);
        Users user = new Users();
        user.setPreferredLanguage(Language.YO);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("2348012345678", null, List.of()));
        when(usersRepository.findByPhonenumber("2348012345678")).thenReturn(Optional.of(user));
        when(readingRepository.save(any(DetectorReading.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = controller.getRecommendation(
                new SensorReadingRequest(22.0, 50.0, 40.0, Language.FR), null, null);

        assertEquals("yo", response.getBody().get("language"));
        assertEquals("Àyíká wà láàrín àwọn ìwọ̀n tó yẹ.", response.getBody().get("message"));
    }
}
