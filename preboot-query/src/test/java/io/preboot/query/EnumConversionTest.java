package io.preboot.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.preboot.query.config.TestContainersConfig;
import io.preboot.query.testdata.OrderStatus;
import io.preboot.query.testdata.TestOrder;
import io.preboot.query.testdata.TestOrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestContainersConfig.class)
@Transactional
@Sql("/test-data.sql")
public class EnumConversionTest {

    @Autowired
    private TestOrderRepository orderRepository;

    @Test
    void testEnumEquality_WithEnumValue_ShouldConvertToString() {
        // Given - use existing test data that has status 'COMPLETED'
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.COMPLETED))
                .build();

        // When
        Page<TestOrder> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(TestOrder::getStatus).containsOnly("COMPLETED");
    }

    @Test
    void testEnumEquality_BothEnumAndString_ShouldProduceSameResults() {
        // Given
        SearchParams enumParams = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.PENDING))
                .build();

        SearchParams stringParams =
                SearchParams.criteria(FilterCriteria.eq("status", "PENDING")).build();

        // When
        Page<TestOrder> enumResult = orderRepository.findAll(enumParams);
        Page<TestOrder> stringResult = orderRepository.findAll(stringParams);

        // Then
        assertThat(enumResult.getContent()).hasSize(2);
        assertThat(stringResult.getContent()).hasSize(2);
        assertThat(enumResult.getContent())
                .extracting(TestOrder::getOrderNumber)
                .containsExactlyInAnyOrderElementsOf(stringResult.getContent().stream()
                        .map(TestOrder::getOrderNumber)
                        .toList());
    }

    @Test
    void testEnumInOperator_WithEnumValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.in("status", OrderStatus.COMPLETED, OrderStatus.CANCELLED))
                .build();

        // When
        Page<TestOrder> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrder::getStatus).containsOnly("COMPLETED", "CANCELLED");
    }

    @Test
    void testEnumWithAndCondition_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.and(
                        List.of(FilterCriteria.eq("status", OrderStatus.COMPLETED), FilterCriteria.gt("amount", 200))))
                .build();

        // When
        Page<TestOrder> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOrderNumber()).isEqualTo("ORD003");
    }

    @Test
    void testCountWithEnumFilter_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.COMPLETED))
                .build();

        // When
        long count = orderRepository.count(params);

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testCountWithComplexEnumFilter_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.and(
                        List.of(FilterCriteria.eq("status", OrderStatus.COMPLETED), FilterCriteria.gte("amount", 300))))
                .build();

        // When
        long count = orderRepository.count(params);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testEnumNotEquals_WithEnumValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.neq("status", OrderStatus.COMPLETED))
                .build();

        // When
        Page<TestOrder> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrder::getStatus).containsOnly("PENDING", "CANCELLED");
    }
}
