/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.clientscanner.probe;

import de.rub.nds.scanner.core.constants.ScannerDetail;
import de.rub.nds.scanner.core.constants.TestResults;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsscanner.clientscanner.config.ClientScannerConfig;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;
import de.rub.nds.tlsscanner.core.constants.TlsAnalyzedProperty;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.tlsscanner.core.leak.PaddingOracleTestInfo;
import de.rub.nds.tlsscanner.core.probe.padding.PaddingOracleAttacker;
import de.rub.nds.tlsscanner.core.probe.padding.constants.PaddingRecordGeneratorType;
import de.rub.nds.tlsscanner.core.probe.padding.constants.PaddingVectorGeneratorType;
import de.rub.nds.tlsscanner.core.probe.result.PaddingOracleResult;
import de.rub.nds.tlsscanner.core.probe.result.VersionSuiteListPair;
import de.rub.nds.tlsscanner.core.vector.statistics.InformationLeakTest;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class PaddingOracleProbe
        extends TlsClientProbe<
                ClientScannerConfig, ClientReport, PaddingOracleResult<ClientReport>> {

    private static final int NUMBER_OF_ITERATIONS = 3;
    private static final int NUMBER_OF_ITERATIONS_IN_QUICK_MODE = 1;
    private static final int NUMBER_OF_ADDTIONAL_ITERATIONS = 7;
    private static final int NUMBER_OF_ADDTIONAL_ITERATIONS_IN_QUICK_MODE = 9;

    private final ScannerDetail scanDetail;
    private final int numberOfIterations;
    private final int numberOfAddtionalIterations;

    private List<VersionSuiteListPair> clientSupportedSuites;
    private boolean sendsApplicationMessage;

    public PaddingOracleProbe(
            ParallelExecutor parallelExecutor, ClientScannerConfig scannerConfig) {
        super(parallelExecutor, TlsProbeType.PADDING_ORACLE, scannerConfig);
        this.scanDetail = scannerConfig.getScanDetail();
        this.numberOfIterations =
                scanDetail.isGreaterEqualTo(ScannerDetail.NORMAL)
                        ? NUMBER_OF_ITERATIONS
                        : NUMBER_OF_ITERATIONS_IN_QUICK_MODE;
        this.numberOfAddtionalIterations =
                scanDetail.isGreaterEqualTo(ScannerDetail.NORMAL)
                        ? NUMBER_OF_ADDTIONAL_ITERATIONS
                        : NUMBER_OF_ADDTIONAL_ITERATIONS_IN_QUICK_MODE;
    }

    @Override
    public PaddingOracleResult executeTest() {
        LOGGER.debug("Starting evaluation");
        List<PaddingVectorGeneratorType> vectorTypeList = createVectorTypeList();
        List<InformationLeakTest<PaddingOracleTestInfo>> testResultList = new LinkedList<>();
        for (PaddingVectorGeneratorType vectorGeneratorType : vectorTypeList) {
            for (VersionSuiteListPair pair : clientSupportedSuites) {
                if (!pair.getVersion().isSSL() && !pair.getVersion().isTLS13()) {
                    for (CipherSuite suite : pair.getCipherSuiteList()) {
                        if (!suite.isPsk()
                                && suite.isCBC()
                                && CipherSuite.getImplemented().contains(suite)) {
                            PaddingRecordGeneratorType recordGeneratorType =
                                    scanDetail.isGreaterEqualTo(ScannerDetail.NORMAL)
                                            ? PaddingRecordGeneratorType.SHORT
                                            : PaddingRecordGeneratorType.VERY_SHORT;
                            testResultList.add(
                                    getPaddingOracleInformationLeakTest(
                                            vectorGeneratorType,
                                            recordGeneratorType,
                                            numberOfIterations,
                                            pair.getVersion(),
                                            suite));
                        }
                    }
                }
            }
        }
        LOGGER.debug("Finished evaluation");
        if (isPotentiallyVulnerable(testResultList)
                || scanDetail.isGreaterEqualTo(ScannerDetail.NORMAL)) {
            LOGGER.debug("Starting extended evaluation");
            for (InformationLeakTest<PaddingOracleTestInfo> fingerprint : testResultList) {
                if (fingerprint.isDistinctAnswers()
                        || scanDetail.isGreaterEqualTo(ScannerDetail.DETAILED)) {
                    extendFingerPrint(fingerprint, numberOfAddtionalIterations);
                }
            }
            LOGGER.debug("Finished extended evaluation");
        }
        return new PaddingOracleResult(testResultList);
    }

    private List<PaddingVectorGeneratorType> createVectorTypeList() {
        List<PaddingVectorGeneratorType> vectorTypeList = new LinkedList<>();
        vectorTypeList.add(PaddingVectorGeneratorType.FINISHED);
        if (Objects.equals(sendsApplicationMessage, Boolean.TRUE)) {
            vectorTypeList.add(PaddingVectorGeneratorType.CLASSIC_DYNAMIC);
            if (scanDetail == ScannerDetail.ALL) {
                vectorTypeList.add(PaddingVectorGeneratorType.CLOSE_NOTIFY);
            }
        }
        return vectorTypeList;
    }

    private InformationLeakTest<PaddingOracleTestInfo> getPaddingOracleInformationLeakTest(
            PaddingVectorGeneratorType vectorGeneratorType,
            PaddingRecordGeneratorType paddingRecordGeneratorType,
            int numberOfIterations,
            ProtocolVersion testedVersion,
            CipherSuite testedSuite) {
        PaddingOracleAttacker attacker =
                new PaddingOracleAttacker(
                        scannerConfig.createConfig(),
                        getParallelExecutor(),
                        paddingRecordGeneratorType,
                        vectorGeneratorType,
                        numberOfIterations,
                        testedVersion,
                        testedSuite);
        if (scanDetail.isGreaterEqualTo(ScannerDetail.DETAILED)) {
            attacker.setAdditionalTimeout(1000);
            attacker.setIncreasingTimeout(true);
        } else {
            attacker.setAdditionalTimeout(50);
        }
        attacker.isVulnerable();
        return new InformationLeakTest<>(
                new PaddingOracleTestInfo(
                        testedVersion,
                        testedSuite,
                        vectorGeneratorType,
                        paddingRecordGeneratorType),
                attacker.getFullResponseMap());
    }

    private void extendFingerPrint(
            InformationLeakTest<PaddingOracleTestInfo> informationLeakTest,
            int numberOfAdditionalIterations) {
        InformationLeakTest<PaddingOracleTestInfo> intermediateResponseMap =
                getPaddingOracleInformationLeakTest(
                        informationLeakTest.getTestInfo().getVectorGeneratorType(),
                        informationLeakTest.getTestInfo().getRecordGeneratorType(),
                        numberOfAdditionalIterations,
                        informationLeakTest.getTestInfo().getVersion(),
                        informationLeakTest.getTestInfo().getCipherSuite());
        informationLeakTest.extendTestWithVectorContainers(
                intermediateResponseMap.getVectorContainerList());
    }

    private boolean isPotentiallyVulnerable(
            List<InformationLeakTest<PaddingOracleTestInfo>> testResultList) {
        for (InformationLeakTest<?> fingerprint : testResultList) {
            if (fingerprint.isDistinctAnswers()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canBeExecuted(ClientReport report) {
        if (report.isProbeAlreadyExecuted(TlsProbeType.PROTOCOL_VERSION)
                && report.isProbeAlreadyExecuted(TlsProbeType.CIPHER_SUITE)
                && report.isProbeAlreadyExecuted(TlsProbeType.APPLICATION_MESSAGE)) {
            return Objects.equals(
                    report.getResult(TlsAnalyzedProperty.SUPPORTS_BLOCK_CIPHERS), TestResults.TRUE);
        } else {
            return false;
        }
    }

    @Override
    public PaddingOracleResult getCouldNotExecuteResult() {
        return new PaddingOracleResult(TestResults.COULD_NOT_TEST);
    }

    @Override
    public void adjustConfig(ClientReport report) {
        clientSupportedSuites = report.getVersionSuitePairs();
        sendsApplicationMessage =
                report.getResult(TlsAnalyzedProperty.SENDS_APPLICATION_MESSAGE) == TestResults.TRUE;
    }
}
