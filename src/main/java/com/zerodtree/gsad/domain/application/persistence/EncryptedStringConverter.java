package com.zerodtree.gsad.domain.application.persistence;

import com.zerodtree.gsad.security.CredentialCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static CredentialCipher cipher;

    @Autowired
    void setCipher(CredentialCipher credentialCipher) {
        EncryptedStringConverter.cipher = credentialCipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (cipher == null || attribute == null) {
            return attribute;
        }
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (cipher == null || dbData == null) {
            return dbData;
        }
        return cipher.decrypt(dbData);
    }
}
