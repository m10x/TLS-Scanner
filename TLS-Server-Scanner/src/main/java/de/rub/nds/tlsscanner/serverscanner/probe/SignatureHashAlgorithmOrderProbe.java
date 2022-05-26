/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.probe;

import de.rub.nds.scanner.core.constants.TestResult;
import de.rub.nds.scanner.core.constants.TestResults;
import de.rub.nds.scanner.core.probe.requirements.Requirement;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.core.constants.TlsAnalyzedProperty;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.tlsscanner.core.probe.TlsProbe;
import de.rub.nds.tlsscanner.serverscanner.config.ServerScannerConfig;
import de.rub.nds.tlsscanner.core.probe.requirements.ProbeRequirement;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class SignatureHashAlgorithmOrderProbe extends TlsProbe<ServerScannerConfig, ServerReport> {

    private TestResult enforced;

    public SignatureHashAlgorithmOrderProbe(ServerScannerConfig scannerConfig, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, TlsProbeType.SIGNATURE_HASH_ALGORITHM_ORDER, scannerConfig);
        super.register(TlsAnalyzedProperty.ENFORCES_SIGNATURE_HASH_ALGORITHM_ORDERING);
    }

    @Override
    public void executeTest() {
        List<SignatureAndHashAlgorithm> toTestList = new LinkedList<>();
        toTestList.addAll(Arrays.asList(SignatureAndHashAlgorithm.values()));
        SignatureAndHashAlgorithm firstSelectedSignatureAndHashAlgorithm =
            getSelectedSignatureAndHashAlgorithm(toTestList);
        Collections.reverse(toTestList);
        SignatureAndHashAlgorithm secondSelectedSignatureAndHashAlgorithm =
            getSelectedSignatureAndHashAlgorithm(toTestList);
        enforced = firstSelectedSignatureAndHashAlgorithm == secondSelectedSignatureAndHashAlgorithm ? TestResults.TRUE
            : TestResults.FALSE;
    }

    @Override
    protected Requirement getRequirements(ServerReport report) {
        ProbeRequirement preq =
            new ProbeRequirement(report).requireProbeTypes(TlsProbeType.SIGNATURE_HASH_ALGORITHM_ORDER);
        return new ProbeRequirement(report).notRequirement(preq);
    }

    @Override
    public void adjustConfig(ServerReport report) {
    }

    private SignatureAndHashAlgorithm getSelectedSignatureAndHashAlgorithm(List<SignatureAndHashAlgorithm> list) {
        Config config = getScannerConfig().createConfig();

        config.setAddSignatureAndHashAlgorithmsExtension(true);
        config.setDefaultClientSupportedSignatureAndHashAlgorithms(list);
        config.setWorkflowTraceType(WorkflowTraceType.DYNAMIC_HELLO);

        config.setEarlyStop(true);
        config.setStopActionsAfterIOException(true);
        config.setEnforceSettings(true);
        config.setQuickReceive(true);
        config.setStopActionsAfterFatal(true);
        config.setStopReceivingAfterFatal(true);

        State state = new State(config);
        executeState(state);
        return state.getTlsContext().getSelectedSignatureAndHashAlgorithm();
    }

    @Override
    protected void mergeData(ServerReport report) {
        super.put(TlsAnalyzedProperty.ENFORCES_SIGNATURE_HASH_ALGORITHM_ORDERING, enforced);
    }
}
