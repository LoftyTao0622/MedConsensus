package com.zyt.medconsensus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class ExternalIdentityServiceTests {

    private static final String PROVIDER = "wechat";
    private static final String APP_ID = "test-app";

    @Autowired ExternalIdentityService identities;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void sameSubjectAndLocalIdCanRepresentEachRoleIndependently() {
        long id = newId();
        String phone = newPhone();
        jdbc.update("""
                INSERT INTO doctor_basic_info(id,username,password_hash,phone,invite_code) VALUES(?,?,?,?,?)
                """, id, "doctor-" + id, passwords.encode("doctor-password"), phone, newPhone());
        createPatient(id, phone);
        String subject = UUID.randomUUID().toString();

        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT")).isEmpty();
        identities.bind(PROVIDER, APP_ID, subject, "DOCTOR", phone, "doctor-password");
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT")).isEmpty();
        assertThatThrownBy(() -> identities.bind(PROVIDER, APP_ID, subject, "PATIENT", phone, "doctor-password"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        identities.bind(PROVIDER, APP_ID, subject, "PATIENT", phone, "patient-password");
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "DOCTOR").orElseThrow().role()).isEqualTo("DOCTOR");
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT").orElseThrow().role()).isEqualTo("PATIENT");
        assertThat(identities.lookup(PROVIDER, "another-app", subject, "DOCTOR")).isEmpty();
        assertThat(identities.lookup("another-provider", APP_ID, subject, "PATIENT")).isEmpty();
        jdbc.update("DELETE FROM patient_account WHERE id=?", id);
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT")).isEmpty();
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "DOCTOR")).isPresent();
    }

    @Test
    void conflictsDoNotReplaceEitherAccountAndRetriesStillRequireThePassword() {
        long alice = newId();
        long bob = newId();
        String alicePhone = newPhone();
        String bobPhone = newPhone();
        createPatient(alice, alicePhone);
        createPatient(bob, bobPhone);
        String subject = UUID.randomUUID().toString();

        identities.bind(PROVIDER, APP_ID, subject, "PATIENT", alicePhone, "patient-password");
        assertThat(identities.bind(PROVIDER, APP_ID, subject, "PATIENT", alicePhone, "patient-password").id()).isEqualTo(alice);
        assertThatThrownBy(() -> identities.bind(PROVIDER, APP_ID, subject, "PATIENT", alicePhone, "wrong-password"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> identities.bind(PROVIDER, APP_ID, subject, "PATIENT", bobPhone, "patient-password"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> identities.bind(PROVIDER, APP_ID, subject + "-other", "PATIENT", alicePhone, "patient-password"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT").orElseThrow().id()).isEqualTo(alice);
        assertThat(identities.lookup(PROVIDER, APP_ID, subject + "-other", "PATIENT")).isEmpty();

        assertThat(identities.bind(PROVIDER, "another-app", subject, "PATIENT", bobPhone, "patient-password").id()).isEqualTo(bob);
        assertThat(identities.bind("another-provider", APP_ID, subject, "PATIENT", bobPhone, "patient-password").id()).isEqualTo(bob);
        assertThat(identities.lookup(PROVIDER, APP_ID, subject, "PATIENT").orElseThrow().id()).isEqualTo(alice);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCallbacksForOneMappingBothSucceedAndPersistOneRow() throws Exception {
        String phone = newPhone();
        long id = newId();
        createPatient(id, phone);
        String subject = UUID.randomUUID().toString();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> callback = () -> {
                start.await(5, TimeUnit.SECONDS);
                return identities.bind(PROVIDER, APP_ID, subject, "PATIENT", phone, "patient-password").id();
            };
            Future<Long> first = pool.submit(callback);
            Future<Long> second = pool.submit(callback);
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_identity WHERE patient_id=?", Long.class, id))
                    .isEqualTo(1L);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
            jdbc.update("DELETE FROM patient_account WHERE id=?", id);
        }
    }

    private void createPatient(long id, String phone) {
        jdbc.update("""
                INSERT INTO patient_account(id,patient_name,password_hash,phone) VALUES(?,?,?,?)
                """, id, "测试患者", passwords.encode("patient-password"), phone);
    }

    private long newId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000_000L, Long.MAX_VALUE);
    }

    private String newPhone() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
