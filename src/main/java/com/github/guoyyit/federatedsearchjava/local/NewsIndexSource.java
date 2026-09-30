package com.github.guoyyit.federatedsearchjava.local;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class NewsIndexSource extends AbstractInMemorySource {

    public NewsIndexSource() {
        super("news", List.of(
                new Doc("n-3001", "New Mechanical Keyboard Lineup Launches",
                        "The company launched a refreshed wireless mechanical keyboard lineup with improved battery life.",
                        1.2, Instant.parse("2026-09-28T08:00:00Z")),
                new Doc("n-3002", "Office Ergonomics Trends Report",
                        "A new report highlights demand for ergonomic mouse devices, monitor arms and laptop stands.",
                        1.0, Instant.parse("2026-09-25T08:00:00Z")),
                new Doc("n-3003", "4K Monitor Adoption Accelerates",
                        "Analysts note accelerating adoption of 4K USB-C monitors in hybrid work setups.",
                        0.9, Instant.parse("2026-09-22T08:00:00Z")),
                new Doc("p-1002", "Ergonomic Wireless Mouse Wins Award",
                        "The ergonomic wireless mouse received an award for comfort and battery efficiency.",
                        1.4, Instant.parse("2026-09-29T08:00:00Z"))
        ));
    }
}
