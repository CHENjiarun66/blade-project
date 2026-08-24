package com.blade.whatsapp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappContextRedactorTest {
    @Test
    void removesPhoneEmailUrlAndWhatsappJid() {
        String input = "联系 +86 138-0000-0000 或 buyer@example.com https://example.com/a 8613800000000@s.whatsapp.net";
        String result = WhatsappContextRedactor.sanitize(input);
        assertThat(result).doesNotContain("138-0000", "buyer@example.com", "https://", "s.whatsapp.net")
                .contains("[PHONE]", "[EMAIL]", "[LINK]", "[CONTACT]");
    }

    @Test
    void limitsSingleMessageContext() {
        assertThat(WhatsappContextRedactor.limit("123456", 4)).isEqualTo("1234…");
        assertThat(WhatsappContextRedactor.limit("1234", 4)).isEqualTo("1234");
    }
}
