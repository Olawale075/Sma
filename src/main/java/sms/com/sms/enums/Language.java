package sms.com.sms.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Language {
    EN, YO, FR;

    @JsonValue
    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Language fromCode(String code) {
        if (code == null) {
            return null;
        }

        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported language code: " + code, exception);
        }
    }
}
