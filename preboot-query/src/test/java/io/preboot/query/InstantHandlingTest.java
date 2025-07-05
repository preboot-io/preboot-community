package io.preboot.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.preboot.query.config.TestContainersConfig;
import io.preboot.query.testdata.TestEvent;
import io.preboot.query.testdata.TestEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestContainersConfig.class)
@Transactional
@Sql("/test-data-instant.sql")
public class InstantHandlingTest {

    // Test data timestamps (actual values as read from database after timezone conversion)
    private static final Instant USER_REGISTRATION_TIME = Instant.parse("2024-01-01T09:00:00Z");
    private static final Instant PAYMENT_PROCESSED_TIME = Instant.parse("2024-01-02T10:30:00Z");
    private static final Instant ORDER_CREATED_TIME = Instant.parse("2024-01-03T13:45:00Z");
    private static final Instant USER_LOGIN_TIME = Instant.parse("2024-01-04T08:15:00Z");

    // Cutoff times for range queries
    private static final Instant CUTOFF_AFTER_JAN_4_NOON = Instant.parse("2024-01-04T12:00:00Z");
    private static final Instant CUTOFF_BEFORE_JAN_3_MIDNIGHT = Instant.parse("2024-01-03T00:00:00Z");
    private static final Instant RANGE_START_JAN_2 = Instant.parse("2024-01-02T00:00:00Z");
    private static final Instant RANGE_END_JAN_5 = Instant.parse("2024-01-05T00:00:00Z");

    // Additional cutoff times for specific tests
    private static final Instant JAN_1_MIDNIGHT = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant JAN_4_MIDNIGHT = Instant.parse("2024-01-04T00:00:00Z");
    private static final Instant CUTOFF_AFTER_JAN_3_AFTERNOON = Instant.parse("2024-01-03T13:45:00Z");
    private static final Instant CUTOFF_AFTER_JAN_5_AFTERNOON = Instant.parse("2024-01-05T15:20:00Z");

    // String representations for string-based tests
    private static final String PAYMENT_PROCESSED_TIME_STR = "2024-01-02T10:30:00Z";
    private static final String ORDER_CREATED_TIME_STR = "2024-01-03T13:45:00Z";
    private static final String CUTOFF_BEFORE_JAN_3_MIDNIGHT_STR = "2024-01-03T00:00:00Z";
    private static final String RANGE_START_JAN_2_STR = "2024-01-02T00:00:00Z";
    private static final String RANGE_END_JAN_5_STR = "2024-01-05T00:00:00Z";
    private static final String PAYMENT_PROCESSED_ALT_TIME_STR = "2024-01-02T10:30:00Z";
    private static final String PAYMENT_FAILED_ALT_TIME_STR = "2024-01-05T15:20:00Z";

    @Autowired
    private TestEventRepository eventRepository;

    @Test
    void testInstantEquality_WithInstantValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("eventTimestamp", PAYMENT_PROCESSED_TIME))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventName()).isEqualTo("Payment Processed");
        assertThat(result.getContent().get(0).getEventTimestamp()).isEqualTo(PAYMENT_PROCESSED_TIME);
    }

    @Test
    void testInstantEquality_WithStringValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("eventTimestamp", PAYMENT_PROCESSED_TIME_STR))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventName()).isEqualTo("Payment Processed");
    }

    @Test
    void testInstantEquality_BothInstantAndString_ShouldProduceSameResults() {
        // Given
        SearchParams instantParams = SearchParams.criteria(FilterCriteria.eq("eventTimestamp", ORDER_CREATED_TIME))
                .build();

        SearchParams stringParams = SearchParams.criteria(FilterCriteria.eq("eventTimestamp", ORDER_CREATED_TIME_STR))
                .build();

        // When
        Page<TestEvent> instantResult = eventRepository.findAll(instantParams);
        Page<TestEvent> stringResult = eventRepository.findAll(stringParams);

        // Then
        assertThat(instantResult.getContent()).hasSize(1);
        assertThat(stringResult.getContent()).hasSize(1);
        assertThat(instantResult.getContent().get(0).getEventName())
                .isEqualTo(stringResult.getContent().get(0).getEventName());
        assertThat(instantResult.getContent().get(0).getEventName()).isEqualTo("Order Created");
    }

    @Test
    void testInstantGreaterThan_WithInstantValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.gt("eventTimestamp", CUTOFF_AFTER_JAN_4_NOON))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Failed", "Order Shipped", "User Logout");
    }

    @Test
    void testInstantLessThan_WithStringValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.lt("eventTimestamp", CUTOFF_BEFORE_JAN_3_MIDNIGHT_STR))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("User Registration", "Payment Processed");
    }

    @Test
    void testInstantBetween_WithInstantValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.between("eventTimestamp", RANGE_START_JAN_2, RANGE_END_JAN_5))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Processed", "Order Created", "User Login");
    }

    @Test
    void testInstantBetween_WithStringValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.between("eventTimestamp", RANGE_START_JAN_2_STR, RANGE_END_JAN_5_STR))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Processed", "Order Created", "User Login");
    }

    @Test
    void testInstantNotEquals_WithInstantValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.neq("eventTimestamp", USER_LOGIN_TIME))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(6);
        assertThat(result.getContent()).extracting(TestEvent::getEventName).doesNotContain("User Login");
    }

    @Test
    void testInstantGreaterThanOrEqual_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.gte("eventTimestamp", CUTOFF_AFTER_JAN_5_AFTERNOON))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Failed", "Order Shipped", "User Logout");
    }

    @Test
    void testInstantLessThanOrEqual_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.lte("eventTimestamp", CUTOFF_AFTER_JAN_3_AFTERNOON))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("User Registration", "Payment Processed", "Order Created");
    }

    @Test
    void testInstantWithAndCondition_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.and(List.of(
                        FilterCriteria.gt("eventTimestamp", JAN_1_MIDNIGHT),
                        FilterCriteria.eq("eventType", "PAYMENT_EVENT"))))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Processed", "Payment Failed");
    }

    @Test
    void testCountWithInstantFilter_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.gt("eventTimestamp", JAN_4_MIDNIGHT))
                .build();

        // When
        long count = eventRepository.count(params);

        // Then
        assertThat(count).isEqualTo(4);
    }

    @Test
    void testInstantWithSorting_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("eventType", "USER_EVENT"))
                .sortDirection(Sort.Direction.DESC)
                .sortField("eventTimestamp")
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactly("User Logout", "User Login", "User Registration");
    }

    @Test
    void testInstantWithNullHandling_ShouldWork() {
        // Given
        SearchParams params =
                SearchParams.criteria(FilterCriteria.isNull("updatedAt")).build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventName()).isEqualTo("Order Shipped");
        assertThat(result.getContent().get(0).getUpdatedAt()).isNull();
    }

    @Test
    void testInstantWithNotNullHandling_ShouldWork() {
        // Given
        SearchParams params =
                SearchParams.criteria(FilterCriteria.isNotNull("updatedAt")).build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(6);
        assertThat(result.getContent())
                .allSatisfy(event -> assertThat(event.getUpdatedAt()).isNotNull());
    }

    @Test
    void testInstantInOperator_WithInstantValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.in("eventTimestamp", USER_REGISTRATION_TIME, CUTOFF_AFTER_JAN_3_AFTERNOON))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("User Registration", "Order Created");
    }

    @Test
    void testInstantInOperator_WithStringValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.in(
                        "eventTimestamp", PAYMENT_PROCESSED_ALT_TIME_STR, PAYMENT_FAILED_ALT_TIME_STR))
                .build();

        // When
        Page<TestEvent> result = eventRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TestEvent::getEventName)
                .containsExactlyInAnyOrder("Payment Processed", "Payment Failed");
    }
}
