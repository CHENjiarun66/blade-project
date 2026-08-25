package com.blade.whatsapp.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappPhoneMatcherTest {
    @Test
    void combinesCustomerCountryCodeWithLocalPhone() {
        assertThat(WhatsappPhoneMatcher.customerPhoneVariants("+243", "835 453 734"))
                .contains("835453734", "243835453734");
    }

    @Test
    void doesNotDuplicateCountryCodeAlreadyStoredInPhone() {
        assertThat(WhatsappPhoneMatcher.customerPhoneVariants("+243", "+243 835 453 734"))
                .containsExactly("243835453734");
    }

    @Test
    void removesDomesticTrunkPrefixWhenBuildingInternationalNumber() {
        assertThat(WhatsappPhoneMatcher.customerPhoneVariants("+234", "0803-391-2244"))
                .contains("2348033912244");
    }
}
