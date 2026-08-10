package com.elatusdev.pokedex.identity.infrastructure;

import com.elatusdev.pokedex.identity.domain.PasswordHash;
import org.springframework.stereotype.Component;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

// I10, defence in depth. PasswordHash.toString() already returns "***", but toString is not
// the path Jackson takes — a record is serialised through its components, so a hash reaching
// any response DTO would be emitted verbatim. WF-000 §4.4 names @JsonIgnore as the mechanism;
// that annotation cannot go on the value object, because ArchUnit L2 forbids a Jackson import
// in ..domain.. . Registering the masking on the mapper achieves the same guarantee on the
// layer that owns serialisation.
@Component
public class CredentialMaskingModule extends SimpleModule {

    public CredentialMaskingModule() {
        super("credential-masking");
        addSerializer(PasswordHash.class, new MaskedSerializer());
    }

    private static final class MaskedSerializer extends StdSerializer<PasswordHash> {

        private MaskedSerializer() {
            super(PasswordHash.class);
        }

        @Override
        public void serialize(PasswordHash value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(PasswordHash.MASK);
        }
    }
}
