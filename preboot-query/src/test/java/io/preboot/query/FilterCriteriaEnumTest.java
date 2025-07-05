package io.preboot.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.preboot.query.config.TestContainersConfig;
import io.preboot.query.testdata.OrderStatus;
import io.preboot.query.testdata.TestOrderWithEnum;
import io.preboot.query.testdata.TestOrderWithEnumRepository;
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
@Sql("/test-data-enum.sql")
public class FilterCriteriaEnumTest {

    @Autowired
    private TestOrderWithEnumRepository orderRepository;

    @Test
    void testEnumEquality_WithEnumValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.COMPLETED))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrderWithEnum::getStatus).containsOnly(OrderStatus.COMPLETED);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getOrderNumber)
                .containsExactlyInAnyOrder("ENUM001", "ENUM003", "ENUM007");
    }

    @Test
    void testEnumEquality_WithStringValue_ShouldWork() {
        // Given
        SearchParams params =
                SearchParams.criteria(FilterCriteria.eq("status", "COMPLETED")).build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrderWithEnum::getStatus).containsOnly(OrderStatus.COMPLETED);
    }

    @Test
    void testEnumEquality_BothEnumAndString_ShouldProduceSameResults() {
        // Given
        SearchParams enumParams = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.PENDING))
                .build();

        SearchParams stringParams =
                SearchParams.criteria(FilterCriteria.eq("status", "PENDING")).build();

        // When
        Page<TestOrderWithEnum> enumResult = orderRepository.findAll(enumParams);
        Page<TestOrderWithEnum> stringResult = orderRepository.findAll(stringParams);

        // Then
        assertThat(enumResult.getContent()).hasSize(2);
        assertThat(stringResult.getContent()).hasSize(2);
        assertThat(enumResult.getContent())
                .extracting(TestOrderWithEnum::getOrderNumber)
                .containsExactlyInAnyOrder("ENUM002", "ENUM005");
        assertThat(stringResult.getContent())
                .extracting(TestOrderWithEnum::getOrderNumber)
                .containsExactlyInAnyOrder("ENUM002", "ENUM005");
    }

    @Test
    void testEnumNotEquals_WithEnumValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.neq("status", OrderStatus.COMPLETED))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(4);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getStatus)
                .containsOnly(OrderStatus.PENDING, OrderStatus.CANCELLED);
    }

    @Test
    void testEnumInOperator_WithEnumValues_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.in("status", OrderStatus.COMPLETED, OrderStatus.CANCELLED))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getStatus)
                .containsOnly(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    }

    @Test
    void testEnumInOperator_WithMixedEnumAndString_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.in("status", OrderStatus.COMPLETED, "PENDING"))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getStatus)
                .containsOnly(OrderStatus.COMPLETED, OrderStatus.PENDING);
    }

    @Test
    void testEnumCaseInsensitive_WithEnumValue_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eqic("status", OrderStatus.COMPLETED.name()))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrderWithEnum::getStatus).containsOnly(OrderStatus.COMPLETED);
    }

    @Test
    void testEnumWithAndCondition_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.and(
                        List.of(FilterCriteria.eq("status", OrderStatus.COMPLETED), FilterCriteria.gt("amount", 200))))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getOrderNumber)
                .containsExactlyInAnyOrder("ENUM003", "ENUM007");
    }

    @Test
    void testEnumWithOrCondition_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.or(List.of(
                        FilterCriteria.eq("status", OrderStatus.COMPLETED),
                        FilterCriteria.eq("status", OrderStatus.PENDING))))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getStatus)
                .containsOnly(OrderStatus.COMPLETED, OrderStatus.PENDING);
    }

    @Test
    void testCountWithEnumFilter_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.COMPLETED))
                .build();

        // When
        long count = orderRepository.count(params);

        // Then
        assertThat(count).isEqualTo(3);
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
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testCountWithEnumInFilter_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.in("status", OrderStatus.PENDING, OrderStatus.CANCELLED))
                .build();

        // When
        long count = orderRepository.count(params);

        // Then
        assertThat(count).isEqualTo(4);
    }

    @Test
    void testMultipleEnumFilters_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.and(List.of(
                        FilterCriteria.neq("status", OrderStatus.PENDING),
                        FilterCriteria.neq("status", OrderStatus.CANCELLED))))
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).extracting(TestOrderWithEnum::getStatus).containsOnly(OrderStatus.COMPLETED);
    }

    @Test
    void testEnumWithPagination_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(FilterCriteria.eq("status", OrderStatus.COMPLETED))
                .page(0)
                .size(2)
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getContent()).extracting(TestOrderWithEnum::getStatus).containsOnly(OrderStatus.COMPLETED);
    }

    @Test
    void testEnumWithSorting_ShouldWork() {
        // Given
        SearchParams params = SearchParams.criteria(
                        FilterCriteria.in("status", OrderStatus.COMPLETED, OrderStatus.PENDING))
                .sortDirection(Sort.Direction.DESC)
                .sortField("amount")
                .build();

        // When
        Page<TestOrderWithEnum> result = orderRepository.findAll(params);

        // Then
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.getContent())
                .extracting(TestOrderWithEnum::getOrderNumber)
                .containsExactly("ENUM007", "ENUM005", "ENUM003", "ENUM002", "ENUM001");
    }
}
