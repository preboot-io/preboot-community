package io.preboot.query.testdata;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("events")
public class TestEvent {
    @Id
    private Long id;

    private String eventName;
    private String eventType;
    private BigDecimal eventValue;
    private Instant eventTimestamp;
    private Instant createdAt;
    private Instant updatedAt;
}
