/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.core.afterprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.rub.nds.scanner.core.passive.ExtractedValueContainer;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsscanner.core.passive.TrackableValueType;
import de.rub.nds.tlsscanner.core.report.TlsScanReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class DtlsRetransmissionAfterProbeTest {

    private TlsScanReport report;
    private DtlsRetransmissionAfterProbe probe;
    private ExtractedValueContainer<HandshakeMessageType> retransmissionsContainer;

    @BeforeEach
    public void setup() {
        report = new TlsCoreTestReport();
        probe = new DtlsRetransmissionAfterProbe();
        retransmissionsContainer =
                new ExtractedValueContainer<>(TrackableValueType.DTLS_RETRANSMISSIONS);
    }

    @Test
    public void testNoRetransmissions() {
        report.setExtractedValueContainerList(
                Collections.singletonMap(
                        TrackableValueType.DTLS_RETRANSMISSIONS, retransmissionsContainer));

        probe.analyze(report);

        assertEquals(0, report.getTotalReceivedRetransmissions());
        assertEquals(0, report.getRetransmissionCounters().size());
    }

    @Test
    public void testMultipleRetransmissions() {
        for (HandshakeMessageType type : HandshakeMessageType.values()) {
            retransmissionsContainer.put(type);
        }
        report.setExtractedValueContainerList(
                Collections.singletonMap(
                        TrackableValueType.DTLS_RETRANSMISSIONS, retransmissionsContainer));

        probe.analyze(report);

        assertEquals(
                Integer.valueOf(HandshakeMessageType.values().length),
                report.getTotalReceivedRetransmissions());
        for (HandshakeMessageType type : HandshakeMessageType.values()) {
            assertEquals(1, report.getRetransmissionCounters().get(type));
        }
    }

    @Test
    public void testRetransmissionsOfOneType() {
        for (HandshakeMessageType type : HandshakeMessageType.values()) {
            retransmissionsContainer =
                    new ExtractedValueContainer<>(TrackableValueType.DTLS_RETRANSMISSIONS);
            retransmissionsContainer.put(type);
            report.setExtractedValueContainerList(
                    Collections.singletonMap(
                            TrackableValueType.DTLS_RETRANSMISSIONS, retransmissionsContainer));

            probe.analyze(report);

            assertEquals(1, report.getTotalReceivedRetransmissions());
            assertEquals(1, report.getRetransmissionCounters().get(type));
        }
    }
}
