package com.refillradar.store;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.refillradar.alert.AlertRecordStore;

/**
 * Proves the documented {@code memory} demo mode actually starts without a database.
 *
 * <p>Exists because the README tells people to run it. Documenting a mode nobody has
 * executed is how a README acquires a command that has never worked - and this one is easy
 * to break, since adding any {@code @Repository} that needs a {@code DataSource} would stop
 * the context loading under this profile while every other test stayed green.
 */
@SpringBootTest
@ActiveProfiles("memory")
class MemoryProfileTest {

    @Autowired
    private MedicationRepository medications;

    @Autowired
    private ContactRepository contacts;

    @Autowired
    private AlertRecordStore alertRecords;

    @Test
    @DisplayName("the memory profile wires the in-memory stores and needs no database")
    void memoryProfileStartsWithNoDatabase() {
        assertThat(AopUtils.getTargetClass(medications).getSimpleName())
                .isEqualTo("InMemoryMedicationRepository");
        assertThat(AopUtils.getTargetClass(contacts).getSimpleName())
                .isEqualTo("InMemoryContactRepository");
        assertThat(AopUtils.getTargetClass(alertRecords).getSimpleName())
                .isEqualTo("InMemoryAlertRecordStore");
    }

    @Test
    @DisplayName("it still stores and retrieves, so the demo is a real demo")
    void inMemoryStoreStillWorks() {
        contacts.setEmail("robert", "robert@example.invalid");
        assertThat(contacts.findEmail("robert")).contains("robert@example.invalid");
    }
}
