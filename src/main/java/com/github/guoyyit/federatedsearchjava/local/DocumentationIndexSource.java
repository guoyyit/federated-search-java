package com.github.guoyyit.federatedsearchjava.local;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DocumentationIndexSource extends AbstractInMemorySource {

    public DocumentationIndexSource() {
        super("docs", List.of(
                new Doc("p-1001", "Wireless Mechanical Keyboard User Guide",
                        "This user guide explains keyboard pairing, battery charging and switch replacement for the wireless mechanical keyboard.",
                        0.2, Instant.parse("2026-07-15T08:00:00Z")),
                new Doc("d-2001", "Keyboard Shortcut Reference",
                        "A complete keyboard shortcut reference for the search console, including navigation and editor commands.",
                        0.8, Instant.parse("2026-07-30T08:00:00Z")),
                new Doc("d-2002", "Monitor Calibration Manual",
                        "Steps to calibrate brightness and color on the 4K USB-C monitor for accurate design work.",
                        0.6, Instant.parse("2026-08-05T08:00:00Z")),
                new Doc("d-2003", "Wireless Troubleshooting FAQ",
                        "Answers to common wireless connection problems, interference and pairing failures for mouse and keyboard.",
                        0.7, Instant.parse("2026-08-12T08:00:00Z"))
        ));
    }
}
