package com.github.guoyyit.federatedsearchjava.local;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class CatalogIndexSource extends AbstractInMemorySource {

    public CatalogIndexSource() {
        super("catalog", List.of(
                new Doc("p-1001", "Wireless Mechanical Keyboard",
                        "Compact wireless mechanical keyboard with hot-swappable switches and long battery life.",
                        2.0, Instant.parse("2026-09-20T08:00:00Z")),
                new Doc("p-1002", "Ergonomic Wireless Mouse",
                        "Lightweight ergonomic wireless mouse with silent clicks and precise sensor for office work.",
                        1.5, Instant.parse("2026-09-18T08:00:00Z")),
                new Doc("p-1003", "4K USB-C Monitor",
                        "27 inch 4K monitor with USB-C charging, adjustable stand and wide color gamut.",
                        1.0, Instant.parse("2026-09-10T08:00:00Z")),
                new Doc("p-1004", "Laptop Stand Aluminum",
                        "Foldable aluminum laptop stand that improves posture and cooling.",
                        0.5, Instant.parse("2026-08-28T08:00:00Z"))
        ));
    }
}
