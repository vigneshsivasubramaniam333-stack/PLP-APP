package com.plp.iam.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.plp.iam.model.converter.UserRoleLegacyMapping;
import com.plp.iam.model.enums.UserRole;

import java.io.IOException;

public class UserRoleJsonDeserializer extends JsonDeserializer<UserRole> {

    @Override
    public UserRole deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String v = p.getValueAsString();
        return UserRoleLegacyMapping.fromLegacyDbString(v);
    }
}
