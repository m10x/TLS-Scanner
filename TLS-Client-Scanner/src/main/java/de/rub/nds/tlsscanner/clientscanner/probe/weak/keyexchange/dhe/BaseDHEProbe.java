/**
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker.
 *
 * Copyright 2017-2019 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsscanner.clientscanner.probe.weak.keyexchange.dhe;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsscanner.clientscanner.client.Orchestrator;
import de.rub.nds.tlsscanner.clientscanner.probe.BaseProbe;
import de.rub.nds.tlsscanner.clientscanner.report.requirements.ProbeRequirements;

public abstract class BaseDHEProbe extends BaseProbe {
    protected final boolean tls13, ec, ff;

    public BaseDHEProbe(Orchestrator orchestrator, boolean tls13, boolean ec, boolean ff) {
        super(orchestrator);
        this.tls13 = tls13;
        this.ec = ec;
        this.ff = ff;
    }

    @Override
    protected ProbeRequirements getRequirements() {
        return BaseDHEFunctionality.getRequirements(tls13, ec, ff);
    }

    public void prepareConfig(Config config) {
        BaseDHEFunctionality.prepareConfig(config, tls13, ec, ff);
    }

}
