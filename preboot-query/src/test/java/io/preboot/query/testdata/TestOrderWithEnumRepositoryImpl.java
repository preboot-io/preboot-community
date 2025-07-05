package io.preboot.query.testdata;

import io.preboot.query.FilterableFragmentContext;
import io.preboot.query.FilterableFragmentImpl;
import org.springframework.stereotype.Repository;

@Repository
class TestOrderWithEnumRepositoryImpl extends FilterableFragmentImpl<TestOrderWithEnum, Long> {
    public TestOrderWithEnumRepositoryImpl(FilterableFragmentContext context) {
        super(context, TestOrderWithEnum.class);
    }
}
