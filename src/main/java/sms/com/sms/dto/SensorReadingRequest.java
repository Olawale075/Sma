package sms.com.sms.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SensorReadingRequest {

    @NotNull(message = "temperature is required")
    @DecimalMin(value = "-50.0", message = "temperature must be greater than or equal to -50°C")
    @DecimalMax(value = "100.0", message = "temperature must be less than or equal to 100°C")
    @JsonAlias({"temp", "temperatureC", "airTemperature"})
    private Double temperature;

    @NotNull(message = "humidity is required")
    @DecimalMin(value = "0.0", message = "humidity must be between 0 and 100%")
    @DecimalMax(value = "100.0", message = "humidity must be between 0 and 100%")
    @JsonAlias({"airHumidity", "relativeHumidity"})
    private Double humidity;

    @NotNull(message = "soilMoisture is required")
    @DecimalMin(value = "0.0", message = "soil moisture must be between 0 and 100%")
    @DecimalMax(value = "100.0", message = "soil moisture must be between 0 and 100%")
    @JsonAlias({"soil_moisture", "moisture", "soilMoistureLevel"})
    private Double soilMoisture;
}
