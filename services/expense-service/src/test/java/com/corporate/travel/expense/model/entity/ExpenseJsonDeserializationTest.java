package com.corporate.travel.expense.model.entity;

import com.corporate.travel.models.ExpenseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Jackson 3 creator-detection behaviour: request bodies that omit fields
 * must keep the entity's @Builder.Default values instead of deserializing to null.
 */
@DisplayName("Expense JSON deserialization")
class ExpenseJsonDeserializationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("should_keepBuilderDefaults_when_fieldsOmitted")
    void should_keepBuilderDefaults_when_fieldsOmitted() {
        Expense expense = mapper.readValue("{\"title\":\"Client visit\"}", Expense.class);

        assertThat(expense.getTitle()).isEqualTo("Client visit");
        assertThat(expense.getItems()).isNotNull().isEmpty();
        assertThat(expense.getStatus()).isEqualTo(ExpenseStatus.DRAFT);
    }

    @Test
    @DisplayName("should_bindItems_when_present")
    void should_bindItems_when_present() {
        Expense expense = mapper.readValue(
                "{\"title\":\"Client visit\",\"items\":[{\"description\":\"Taxi\"}]}", Expense.class);

        assertThat(expense.getItems()).hasSize(1);
        assertThat(expense.getItems().get(0).getDescription()).isEqualTo("Taxi");
    }
}
