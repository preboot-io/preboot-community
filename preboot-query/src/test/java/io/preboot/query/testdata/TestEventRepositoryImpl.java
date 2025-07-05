package io.preboot.query.testdata;

import io.preboot.query.FilterableFragmentContext;
import io.preboot.query.FilterableFragmentImpl;
import org.springframework.stereotype.Repository;

@Repository
class TestEventRepositoryImpl extends FilterableFragmentImpl<TestEvent, Long> {
    public TestEventRepositoryImpl(FilterableFragmentContext context) {
        super(context, TestEvent.class);
    }
}
