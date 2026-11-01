package sms.com.sms.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class LanguageCodeConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language language) {
        return language == null ? null : language.getCode();
    }

    @Override
    public Language convertToEntityAttribute(String code) {
        return Language.fromCode(code);
    }
}
