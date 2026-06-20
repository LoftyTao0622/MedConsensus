package com.zyt.medconsensus.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebSocketUserNamesTests {

    @Test
    void separatesDoctorAndPatientWithSameNumericId() {
        assertThat(WebSocketUserNames.doctor(1L))
                .isNotEqualTo(WebSocketUserNames.forRole("PATIENT", 1L));
    }
}
