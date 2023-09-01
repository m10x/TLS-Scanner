/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.core.afterprobe;

import de.rub.nds.scanner.core.afterprobe.AfterProbe;
import de.rub.nds.scanner.core.constants.MapResult;
import de.rub.nds.scanner.core.passive.ExtractedValueContainer;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsscanner.core.constants.TlsAnalyzedProperty;
import de.rub.nds.tlsscanner.core.passive.TrackableValueType;
import de.rub.nds.tlsscanner.core.report.TlsScanReport;

import java.util.HashMap;
import java.util.Map;

public class DtlsRetransmissionAfterProbe extends AfterProbe<TlsScanReport> {

    @Override
    public void analyze(TlsScanReport report) {
        ExtractedValueContainer<HandshakeMessageType> container =
                report.getExtractedValueContainerMap().get(TrackableValueType.DTLS_RETRANSMISSIONS);

        Map<HandshakeMessageType, Integer> retransmissionCounters = new HashMap<>();
        for (HandshakeMessageType type : container.getExtractedValueList()) {
            if (!retransmissionCounters.containsKey(type)) {
                retransmissionCounters.put(type, 1);
            } else {
                retransmissionCounters.put(type, retransmissionCounters.get(type) + 1);
            }
        }
        report.putResult(
                TlsAnalyzedProperty.MAP_RETRANSMISSION_COUNTERS,
                new MapResult<>(
                        retransmissionCounters,
                        TlsAnalyzedProperty.MAP_RETRANSMISSION_COUNTERS.name()));
        report.setTotalReceivedRetransmissions(container.getNumberOfExtractedValues());
    }
}
