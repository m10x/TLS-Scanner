/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.clientscanner.execution;

import de.rub.nds.scanner.core.execution.ScanJob;
import de.rub.nds.scanner.core.execution.ThreadedScanJobExecutor;
import de.rub.nds.scanner.core.passive.StatsWriter;
import de.rub.nds.scanner.core.probe.ScannerProbe;
import de.rub.nds.tlsattacker.core.state.Context;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsscanner.clientscanner.afterprobe.AlpacaAfterProbe;
import de.rub.nds.tlsscanner.clientscanner.afterprobe.ClientRandomnessAfterProbe;
import de.rub.nds.tlsscanner.clientscanner.afterprobe.DhValueAfterProbe;
import de.rub.nds.tlsscanner.clientscanner.config.ClientScannerConfig;
import de.rub.nds.tlsscanner.clientscanner.probe.AlpnProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.ApplicationMessageProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.BasicProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.CcaSupportProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.CertificateProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.CipherSuiteProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.CompressionProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DheParameterProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsBugsProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsFragmentationProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsHelloVerifyRequestProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsMessageSequenceProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsReorderingProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.DtlsRetransmissionsProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.FreakProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.PaddingOracleProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.ProtocolVersionProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.RecordFragmentationProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.ResumptionProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.SniProbe;
import de.rub.nds.tlsscanner.clientscanner.probe.Version13RandomProbe;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;
import de.rub.nds.tlsscanner.core.afterprobe.DtlsRetransmissionAfterProbe;
import de.rub.nds.tlsscanner.core.afterprobe.EcPublicKeyAfterProbe;
import de.rub.nds.tlsscanner.core.afterprobe.FreakAfterProbe;
import de.rub.nds.tlsscanner.core.afterprobe.LogjamAfterProbe;
import de.rub.nds.tlsscanner.core.afterprobe.PaddingOracleIdentificationAfterProbe;
import de.rub.nds.tlsscanner.core.afterprobe.Sweet32AfterProbe;
import de.rub.nds.tlsscanner.core.config.delegate.CallbackDelegate;
import de.rub.nds.tlsscanner.core.constants.ProtocolType;
import de.rub.nds.tlsscanner.core.execution.TlsScanner;
import de.rub.nds.tlsscanner.core.passive.CbcIvExtractor;
import de.rub.nds.tlsscanner.core.passive.DhPublicKeyExtractor;
import de.rub.nds.tlsscanner.core.passive.DtlsRetransmissionsExtractor;
import de.rub.nds.tlsscanner.core.passive.EcPublicKeyExtractor;
import de.rub.nds.tlsscanner.core.passive.RandomExtractor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;

public final class TlsClientScanner extends TlsScanner {

    private static final Logger LOGGER = LogManager.getLogger();

    private final ParallelExecutor parallelExecutor;
    private final ClientScannerConfig config;
    private boolean closeAfterFinishParallel;

    public TlsClientScanner(ClientScannerConfig config) {
        super(config.getProbes());
        this.config = config;
        parallelExecutor = new ParallelExecutor(config.getOverallThreads(), 3);
        closeAfterFinishParallel = true;
        setCallbacks();
        fillProbeLists();
    }

    public TlsClientScanner(ClientScannerConfig config, ParallelExecutor parallelExecutor) {
        super(config.getProbes());
        this.config = config;
        this.parallelExecutor = parallelExecutor;
        closeAfterFinishParallel = false;
        setCallbacks();
        fillProbeLists();
    }

    @Override
    protected void fillProbeLists() {
        addProbeToProbeList(new BasicProbe(parallelExecutor, config));
        addProbeToProbeList(new ProtocolVersionProbe(parallelExecutor, config));
        addProbeToProbeList(new CipherSuiteProbe(parallelExecutor, config));
        addProbeToProbeList(new CompressionProbe(parallelExecutor, config));
        addProbeToProbeList(new CcaSupportProbe(parallelExecutor, config));
        addProbeToProbeList(new CertificateProbe(parallelExecutor, config));
        addProbeToProbeList(new DheParameterProbe(parallelExecutor, config));
        addProbeToProbeList(new FreakProbe(parallelExecutor, config));
        addProbeToProbeList(new ApplicationMessageProbe(parallelExecutor, config));
        addProbeToProbeList(new PaddingOracleProbe(parallelExecutor, config));
        addProbeToProbeList(new AlpnProbe(parallelExecutor, config));
        addProbeToProbeList(new SniProbe(parallelExecutor, config));
        addProbeToProbeList(new ResumptionProbe(parallelExecutor, config));
        afterList.add(new Sweet32AfterProbe());
        afterList.add(new FreakAfterProbe());
        afterList.add(new LogjamAfterProbe());
        afterList.add(new ClientRandomnessAfterProbe());
        afterList.add(new EcPublicKeyAfterProbe());
        afterList.add(new DhValueAfterProbe());
        afterList.add(new AlpacaAfterProbe());
        afterList.add(new PaddingOracleIdentificationAfterProbe());
        if (config.getDtlsDelegate().isDTLS()) {
            addProbeToProbeList(new DtlsReorderingProbe(parallelExecutor, config));
            addProbeToProbeList(new DtlsFragmentationProbe(parallelExecutor, config));
            addProbeToProbeList(new DtlsHelloVerifyRequestProbe(parallelExecutor, config));
            addProbeToProbeList(new DtlsBugsProbe(parallelExecutor, config));
            addProbeToProbeList(new DtlsMessageSequenceProbe(parallelExecutor, config));
            addProbeToProbeList(new DtlsRetransmissionsProbe(parallelExecutor, config));
            afterList.add(new DtlsRetransmissionAfterProbe());
        } else {
            addProbeToProbeList(new Version13RandomProbe(parallelExecutor, config));
            addProbeToProbeList(new RecordFragmentationProbe(parallelExecutor, config));
            addProbeToProbeList(new ResumptionProbe(parallelExecutor, config));
        }
        // Init StatsWriter
        setDefaultProbeWriter();
    }

    private void setDefaultProbeWriter() {
        for (ScannerProbe probe : probeList) {
            StatsWriter statsWriter = new StatsWriter();
            statsWriter.addExtractor(new RandomExtractor());
            statsWriter.addExtractor(new DhPublicKeyExtractor());
            statsWriter.addExtractor(new EcPublicKeyExtractor());
            statsWriter.addExtractor(new CbcIvExtractor());
            statsWriter.addExtractor(new DtlsRetransmissionsExtractor());
            probe.setWriter(statsWriter);
        }
    }

    public ClientReport scan() {
        adjustServerPort();

        ClientReport clientReport = new ClientReport();
        ScanJob job = new ScanJob(probeList, afterList);
        ThreadedScanJobExecutor<ClientReport> executor =
                new ThreadedScanJobExecutor(config, job, config.getParallelProbes(), "");
        long scanStartTime = System.currentTimeMillis();
        clientReport = executor.execute(clientReport);
        long scanEndTime = System.currentTimeMillis();
        clientReport.setScanStartTime(scanStartTime);
        clientReport.setScanEndTime(scanEndTime);
        ProtocolType protocolType =
                config.getDtlsDelegate().isDTLS() ? ProtocolType.DTLS : ProtocolType.TLS;
        clientReport.setProtocolType(protocolType);

        executor.shutdown();
        closeParallelExecutorIfNeeded();

        return clientReport;
    }

    private void adjustServerPort() {
        if (config.isMultithreaded() && config.getServerDelegate().getPort() != 0) {
            LOGGER.warn(
                    "Configured explicit server port, but also multithreaded execution. Ignoring explicit port.");
            config.getServerDelegate().setPort(0);
        }
    }

    /**
     * Sets the appropriate executor callbacks possibly provided through the callback delegate. The
     * client run command will be executed after the BeforeTransportInitCallback.
     */
    private void setCallbacks() {
        if (config.getCallbackDelegate().getBeforeTransportPreInitCallback() != null
                && parallelExecutor.getDefaultBeforeTransportPreInitCallback() == null) {
            parallelExecutor.setDefaultBeforeTransportPreInitCallback(
                    config.getCallbackDelegate().getBeforeTransportPreInitCallback());
        }

        if (parallelExecutor.getDefaultBeforeTransportInitCallback() == null) {
            parallelExecutor.setDefaultBeforeTransportInitCallback(
                    CallbackDelegate.mergeCallbacks(
                            config.getCallbackDelegate().getBeforeTransportInitCallback(),
                            config.getRunCommandExecutionCallback()));
        }

        if (config.getCallbackDelegate().getAfterTransportInitCallback() != null
                && parallelExecutor.getDefaultAfterTransportInitCallback() == null) {
            parallelExecutor.setDefaultAfterTransportInitCallback(
                    config.getCallbackDelegate().getAfterTransportInitCallback());
        }

        if (parallelExecutor.getDefaultAfterExecutionCallback() == null) {
            parallelExecutor.setDefaultAfterExecutionCallback(
                    CallbackDelegate.mergeCallbacks(
                            config.getCallbackDelegate().getAfterExecutionCallback(),
                            getKillAllSpawnedSubprocessesCallback()));
        }
    }

    /**
     * Provides a callback that kills all the processes that have been spawned during this state
     * execution.
     *
     * @return A callback that kills all spawned subprocesses
     */
    private Function<Context, Integer> getKillAllSpawnedSubprocessesCallback() {
        return (Context state) -> {
            state.killAllSpawnedSubprocesses();
            return 0;
        };
    }

    private void closeParallelExecutorIfNeeded() {
        if (closeAfterFinishParallel) {
            parallelExecutor.shutdown();
        }
    }

    public void setCloseAfterFinishParallel(boolean closeAfterFinishParallel) {
        this.closeAfterFinishParallel = closeAfterFinishParallel;
    }

    public boolean isCloseAfterFinishParallel() {
        return closeAfterFinishParallel;
    }
}
