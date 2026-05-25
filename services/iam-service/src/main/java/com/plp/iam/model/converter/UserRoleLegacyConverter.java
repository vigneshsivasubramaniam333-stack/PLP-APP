package com.plp.iam.model.converter;

import com.plp.iam.model.enums.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps legacy DB role strings to current enum constants.
 */
@Converter(autoApply = false)
public class UserRoleLegacyConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return UserRoleLegacyMapping.fromLegacyDbString(dbData);
    }
}
