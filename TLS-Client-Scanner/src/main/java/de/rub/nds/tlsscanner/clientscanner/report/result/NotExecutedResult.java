/**
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker.
 *
 * Copyright 2017-2019 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsscanner.clientscanner.report.result;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import de.rub.nds.tlsscanner.clientscanner.probe.IProbe;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;

@XmlAccessorType(XmlAccessType.FIELD)
public class NotExecutedResult extends ClientProbeResult {
    @XmlTransient
    private final Class<? extends IProbe> probe;
    public final String message;

    public NotExecutedResult(Class<? extends IProbe> probe, String message) {
        this.probe = probe;
        this.message = message;
    }

    public static NotExecutedResult UNKNOWN_ERROR(Class<? extends IProbe> probe) {
        return new NotExecutedResult(probe, "An unknown error caused this probe to not be executed.");
    }

    public static NotExecutedResult MISSING_DEPENDENT_RESULT(Class<? extends IProbe> probe,
            Class<? extends IProbe> missingProbe) {
        return new NotExecutedResult(probe,
                "This Probe could not be executed, as it depends on the result of the following probe (which is missing): "
                        + missingProbe.getName());
    }

    @Override
    public void merge(ClientReport report) {
        report.putResult(probe, this);
    }

}
