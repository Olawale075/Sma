package sms.com.sms;

import org.junit.jupiter.api.Test;
import sms.com.sms.config.CameraWebSocketHandler;
import sms.com.sms.enums.Language;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ObjectDetectionServiceTest {

    @Test
    void localizesFallbackAdviceAndDisplayLabelsToSelectedLanguage() throws Exception {
        ObjectDetectionService service = new ObjectDetectionService(mock(CameraWebSocketHandler.class));
        String response = """
                {"items":[{"className":"Tomato Leaf Blight","domain":"PLANT","probability":0.9,
                "health":{"status":"diseased","severity":"HIGH"},"treatment":null,
                "prevention":[],"whenToAct":null}]}
                """;

        List<ObjectDetectionService.DetectionResult> results =
                parseDetections(service, response, Language.FR);
        ObjectDetectionService.DetectionResult result = results.get(0);

        assertEquals("Plante", result.domainLabel);
        assertEquals("Malade", result.healthStatusLabel);
        assertEquals("Grave — agissez rapidement", result.severityDescription);
        assertEquals("Retirez les parties atteintes, appliquez un traitement adapté et isolez la plante si nécessaire.",
                result.treatment);
        assertEquals("Agissez dans un délai de 1 à 2 jours.", result.whenToAct);
        assertTrue(result.prevention.stream().allMatch(tip ->
                !tip.contains("Use") && !tip.contains("Ensure") && !tip.contains("Rotate")));
    }

    @Test
    void localizesGeneratedFallbackSummaryToSelectedLanguage() throws Exception {
        ObjectDetectionService service = new ObjectDetectionService(mock(CameraWebSocketHandler.class));
        String response = """
                {"items":[{"className":"Cassava Leaf","domain":"PLANT","probability":0.9,
                "health":{"status":"healthy","severity":"NONE"}}]}
                """;

        List<ObjectDetectionService.DetectionResult> results =
                parseDetections(service, response, Language.YO);
        Method buildSummary = ObjectDetectionService.class.getDeclaredMethod(
                "buildSummary", List.class, Language.class);
        buildSummary.setAccessible(true);

        String summary = (String) buildSummary.invoke(service, results, Language.YO);

        assertTrue(summary.startsWith("A rí ohun 1."));
        assertEquals("Ohun ọ̀gbìn", results.get(0).domainLabel);
        assertEquals("Ó wà ní ìlera", results.get(0).healthStatusLabel);
    }

    @SuppressWarnings("unchecked")
    private List<ObjectDetectionService.DetectionResult> parseDetections(
            ObjectDetectionService service, String response, Language language) throws Exception {
        Method parse = ObjectDetectionService.class.getDeclaredMethod(
                "parseDetections", String.class, String.class, Language.class);
        parse.setAccessible(true);
        return (List<ObjectDetectionService.DetectionResult>) parse.invoke(
                service, response, "General", language);
    }
}
