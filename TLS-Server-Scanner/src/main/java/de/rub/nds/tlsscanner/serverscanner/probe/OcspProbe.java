/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, Technology Innovation Institute, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.serverscanner.probe;

import static de.rub.nds.tlsattacker.core.certificate.ocsp.OCSPResponseTypes.NONCE;

import de.rub.nds.asn1.Asn1Encodable;
import de.rub.nds.asn1.encoder.Asn1Encoder;
import de.rub.nds.asn1.model.Asn1EncapsulatingOctetString;
import de.rub.nds.asn1.model.Asn1ObjectIdentifier;
import de.rub.nds.asn1.model.Asn1PrimitiveOctetString;
import de.rub.nds.asn1.model.Asn1Sequence;
import de.rub.nds.scanner.core.probe.requirements.ProbeRequirement;
import de.rub.nds.scanner.core.probe.requirements.Requirement;
import de.rub.nds.scanner.core.probe.result.TestResult;
import de.rub.nds.scanner.core.probe.result.TestResults;
import de.rub.nds.tlsattacker.core.certificate.ocsp.CertificateInformationExtractor;
import de.rub.nds.tlsattacker.core.certificate.ocsp.OCSPRequest;
import de.rub.nds.tlsattacker.core.certificate.ocsp.OCSPRequestMessage;
import de.rub.nds.tlsattacker.core.certificate.ocsp.OCSPResponseParser;
import de.rub.nds.tlsattacker.core.certificate.ocsp.OCSPResponseTypes;
import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.HandshakeMessageType;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.PskKeyExchangeMode;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateMessage;
import de.rub.nds.tlsattacker.core.protocol.message.CertificateStatusMessage;
import de.rub.nds.tlsattacker.core.protocol.message.cert.CertificateEntry;
import de.rub.nds.tlsattacker.core.protocol.message.extension.CertificateStatusRequestExtensionMessage;
import de.rub.nds.tlsattacker.core.protocol.message.extension.ExtensionMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTraceUtil;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.core.constants.TlsAnalyzedProperty;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.tlsscanner.core.probe.certificate.CertificateChain;
import de.rub.nds.tlsscanner.serverscanner.probe.result.ocsp.OcspCertificateResult;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import de.rub.nds.tlsscanner.serverscanner.selector.ConfigSelector;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.bouncycastle.crypto.tls.Certificate;

public class OcspProbe extends TlsServerProbe {

    private List<CertificateChain> serverCertChains;
    private List<NamedGroup> tls13NamedGroups;

    private List<OcspCertificateResult> certResults;
    private List<CertificateStatusRequestExtensionMessage> tls13CertStatus = null;

    public static final int NONCE_TEST_VALUE_1 = 42;
    public static final int NONCE_TEST_VALUE_2 = 1337;
    private static final long STAPLED_NONCE_RANDOM_SEED = 42;
    private static final int STAPLED_NONCE_RANDOM_BIT_LENGTH = 128;

    public OcspProbe(ConfigSelector configSelector, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, TlsProbeType.OCSP, configSelector);
        register(
                TlsAnalyzedProperty.SUPPORTS_OCSP,
                TlsAnalyzedProperty.SUPPORTS_OCSP_STAPLING,
                TlsAnalyzedProperty.INCLUDES_CERTIFICATE_STATUS_MESSAGE,
                TlsAnalyzedProperty.SUPPORTS_STAPLED_NONCE,
                TlsAnalyzedProperty.MUST_STAPLE,
                TlsAnalyzedProperty.SUPPORTS_NONCE,
                TlsAnalyzedProperty.STAPLED_RESPONSE_EXPIRED,
                TlsAnalyzedProperty.SUPPORTS_CERTIFICATE_STATUS_REQUEST_TLS13,
                TlsAnalyzedProperty.STAPLING_TLS13_MULTIPLE_CERTIFICATES,
                TlsAnalyzedProperty.OCSP_RESULTS);
    }

    @Override
    protected void executeTest() {
        certResults = new LinkedList<>();
        for (CertificateChain serverCertChain : serverCertChains) {
            OcspCertificateResult certResult = new OcspCertificateResult(serverCertChain);

            getMustStaple(serverCertChain.getCertificate(), certResult);
            if (configSelector.foundWorkingConfig()) {
                getStapledResponse(certResult);
            }
            performRequest(serverCertChain.getCertificate(), certResult);

            certResults.add(certResult);
        }
        if (!tls13NamedGroups.isEmpty()) {
            tls13CertStatus = getCertificateStatusFromCertificateEntryExtension();
        }
    }

    private void getMustStaple(Certificate certChain, OcspCertificateResult certResult) {
        org.bouncycastle.asn1.x509.Certificate singleCert = certChain.getCertificateAt(0);
        CertificateInformationExtractor certInformationExtractor =
                new CertificateInformationExtractor(singleCert);
        try {
            certResult.setMustStaple(certInformationExtractor.getMustStaple());
        } catch (Exception e) {
            if (e.getCause() instanceof InterruptedException) {
                LOGGER.error("Timeout on " + getProbeName());
            } else {
                LOGGER.warn("Couldn't determine OCSP must staple flag in certificate.");
            }
        }
    }

    private void getStapledResponse(OcspCertificateResult certResult) {
        Config tlsConfig = configSelector.getBaseConfig();
        tlsConfig.setWorkflowTraceType(WorkflowTraceType.DYNAMIC_HELLO);
        tlsConfig.setCertificateStatusRequestExtensionRequestExtension(prepareNonceExtension());
        tlsConfig.setAddCertificateStatusRequestExtension(true);

        State state = new State(tlsConfig);
        executeState(state);
        List<ExtensionType> supportedExtensions =
                new ArrayList<>(state.getTlsContext().getNegotiatedExtensionSet());

        CertificateStatusMessage certificateStatusMessage = null;
        if (supportedExtensions.contains(ExtensionType.STATUS_REQUEST)) {
            certResult.setSupportsStapling(true);
            if (WorkflowTraceUtil.didReceiveMessage(
                    HandshakeMessageType.CERTIFICATE_STATUS, state.getWorkflowTrace())) {
                certificateStatusMessage =
                        (CertificateStatusMessage)
                                WorkflowTraceUtil.getFirstReceivedMessage(
                                        HandshakeMessageType.CERTIFICATE_STATUS,
                                        state.getWorkflowTrace());
            }
        } else {
            certResult.setSupportsStapling(false);
        }

        if (certificateStatusMessage != null) {
            try {
                certResult.setStapledResponse(
                        OCSPResponseParser.parseResponse(
                                certificateStatusMessage.getOcspResponseBytes().getValue()));
            } catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) {
                    LOGGER.error("Timeout on " + getProbeName());
                } else {
                    LOGGER.warn("Stapled OCSP message could not be parsed!");
                }
            }
        }
    }

    private void performRequest(
            Certificate serverCertificateChain, OcspCertificateResult certResult) {
        CertificateInformationExtractor mainCertExtractor =
                new CertificateInformationExtractor(serverCertificateChain.getCertificateAt(0));
        URL ocspResponderUrl;

        try {
            // Check if leaf certificate supports OCSP
            ocspResponderUrl = new URL(mainCertExtractor.getOcspServerUrl());
        } catch (MalformedURLException ex) {
            LOGGER.debug("Failed to parse a valid OCSP url for OCSP request");
            return;
        }
        certResult.setSupportsOcsp(true);

        OCSPRequest ocspRequest = new OCSPRequest(serverCertificateChain, ocspResponderUrl);

        // First Request Message with first fixed nonce test value
        OCSPRequestMessage ocspFirstRequestMessage = ocspRequest.createDefaultRequestMessage();
        ocspFirstRequestMessage.setNonce(new BigInteger(String.valueOf(NONCE_TEST_VALUE_1)));
        ocspFirstRequestMessage.addExtension(OCSPResponseTypes.NONCE.getOID());
        try {
            certResult.setFirstResponse(ocspRequest.makeRequest(ocspFirstRequestMessage));
            certResult.setHttpGetResponse(ocspRequest.makeGetRequest(ocspFirstRequestMessage));
        } catch (RuntimeException ex) {
            LOGGER.warn("Request with OCSP url failed: " + ex.getMessage());
        }
        // If nonce is supported used, check if server actually replies
        // with a different one immediately after
        if (certResult.getFirstResponse() != null
                && certResult.getFirstResponse().getNonce() != null) {
            certResult.setSupportsNonce(true);
            OCSPRequestMessage ocspSecondRequestMessage = ocspRequest.createDefaultRequestMessage();
            ocspSecondRequestMessage.setNonce(new BigInteger(String.valueOf(NONCE_TEST_VALUE_2)));
            ocspSecondRequestMessage.addExtension(OCSPResponseTypes.NONCE.getOID());
            certResult.setSecondResponse(ocspRequest.makeRequest(ocspSecondRequestMessage));
        } else {
            certResult.setSupportsNonce(false);
        }
    }

    private byte[] prepareNonceExtension() {
        Asn1Sequence innerExtensionSequence = new Asn1Sequence();
        Asn1ObjectIdentifier oid = new Asn1ObjectIdentifier();
        oid.setValue(NONCE.getOID());

        Asn1Sequence extensionSequence = new Asn1Sequence();
        innerExtensionSequence.addChild(oid);

        Asn1EncapsulatingOctetString encapsulatingOctetString = new Asn1EncapsulatingOctetString();

        // Nonce
        Asn1PrimitiveOctetString nonceOctetString = new Asn1PrimitiveOctetString();

        Random rand = new Random(STAPLED_NONCE_RANDOM_SEED);
        BigInteger nonce = new BigInteger(STAPLED_NONCE_RANDOM_BIT_LENGTH, rand);

        nonceOctetString.setValue(nonce.toByteArray());
        encapsulatingOctetString.addChild(nonceOctetString);

        innerExtensionSequence.addChild(encapsulatingOctetString);
        extensionSequence.addChild(innerExtensionSequence);

        List<Asn1Encodable> asn1Encodables = new LinkedList<>();
        asn1Encodables.add(extensionSequence);

        Asn1Encoder asn1Encoder = new Asn1Encoder(asn1Encodables);
        return asn1Encoder.encode();
    }

    @Override
    public Requirement<ServerReport> getRequirements() {
        return new ProbeRequirement<>(TlsProbeType.NAMED_GROUPS, TlsProbeType.CERTIFICATE);
    }

    @Override
    public void adjustConfig(ServerReport report) {
        serverCertChains = new LinkedList<>();
        for (CertificateChain chain : report.getCertificateChainList()) {
            serverCertChains.add(chain);
        }
        tls13NamedGroups =
                report.getListResult(TlsAnalyzedProperty.SUPPORTED_TLS13_GROUPS, NamedGroup.class)
                        .getList();
    }

    private List<CertificateStatusRequestExtensionMessage>
            getCertificateStatusFromCertificateEntryExtension() {
        List<CertificateStatusRequestExtensionMessage> certificateStatuses = new LinkedList<>();
        Config tlsConfig = configSelector.getTls13BaseConfig();
        tlsConfig.setWorkflowTraceType(WorkflowTraceType.DYNAMIC_HELLO);
        List<PskKeyExchangeMode> pskKex = new LinkedList<>();
        pskKex.add(PskKeyExchangeMode.PSK_DHE_KE);
        pskKex.add(PskKeyExchangeMode.PSK_KE);
        tlsConfig.setPSKKeyExchangeModes(pskKex);
        tlsConfig.setAddPSKKeyExchangeModesExtension(true);

        State state = new State(tlsConfig);
        executeState(state);
        if (WorkflowTraceUtil.didReceiveMessage(
                HandshakeMessageType.CERTIFICATE, state.getWorkflowTrace())) {
            CertificateMessage certificateMessage =
                    (CertificateMessage)
                            WorkflowTraceUtil.getFirstReceivedMessage(
                                    HandshakeMessageType.CERTIFICATE, state.getWorkflowTrace());
            List<CertificateEntry> certificateEntries =
                    certificateMessage.getCertificatesListAsEntry();
            for (CertificateEntry certificateEntry : certificateEntries) {
                for (ExtensionMessage extensionMessage : certificateEntry.getExtensions()) {
                    if (extensionMessage instanceof CertificateStatusRequestExtensionMessage) {
                        certificateStatuses.add(
                                (CertificateStatusRequestExtensionMessage) extensionMessage);
                    }
                }
            }
        }
        return certificateStatuses;
    }

    @Override
    protected void mergeData(ServerReport report) {
        put(TlsAnalyzedProperty.OCSP_RESULTS, certResults);
        put(TlsAnalyzedProperty.SUPPORTS_OCSP, getConclusiveSupportsOcsp());
        put(TlsAnalyzedProperty.SUPPORTS_OCSP_STAPLING, getConclusiveSupportsStapling());
        put(
                TlsAnalyzedProperty.INCLUDES_CERTIFICATE_STATUS_MESSAGE,
                getConclusiveIncludesCertMessage());
        put(TlsAnalyzedProperty.SUPPORTS_STAPLED_NONCE, getConclusiveSupportsStapledNonce());
        put(TlsAnalyzedProperty.MUST_STAPLE, getConclusiveMustStaple());
        put(TlsAnalyzedProperty.SUPPORTS_NONCE, getConclusiveSupportsNonce());
        put(TlsAnalyzedProperty.STAPLED_RESPONSE_EXPIRED, getConclusiveStapledResponseExpired());

        if (tls13CertStatus != null) {
            if (tls13CertStatus.size() == 1) {
                put(
                        TlsAnalyzedProperty.SUPPORTS_CERTIFICATE_STATUS_REQUEST_TLS13,
                        TestResults.TRUE);
                put(TlsAnalyzedProperty.STAPLING_TLS13_MULTIPLE_CERTIFICATES, TestResults.FALSE);
            } else if (tls13CertStatus.size() > 1) {
                put(
                        TlsAnalyzedProperty.SUPPORTS_CERTIFICATE_STATUS_REQUEST_TLS13,
                        TestResults.TRUE);
                put(TlsAnalyzedProperty.STAPLING_TLS13_MULTIPLE_CERTIFICATES, TestResults.TRUE);
            } else {
                put(
                        TlsAnalyzedProperty.SUPPORTS_CERTIFICATE_STATUS_REQUEST_TLS13,
                        TestResults.FALSE);
                put(TlsAnalyzedProperty.STAPLING_TLS13_MULTIPLE_CERTIFICATES, TestResults.FALSE);
            }
        } else {
            put(
                    TlsAnalyzedProperty.SUPPORTS_CERTIFICATE_STATUS_REQUEST_TLS13,
                    TestResults.COULD_NOT_TEST);
            put(
                    TlsAnalyzedProperty.STAPLING_TLS13_MULTIPLE_CERTIFICATES,
                    TestResults.COULD_NOT_TEST);
        }
    }

    private TestResult getConclusiveSupportsOcsp() {
        boolean foundFalse = false;
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (Boolean.TRUE.equals(result.getSupportsOcsp())) {
                    return TestResults.TRUE;
                } else if (Boolean.FALSE.equals(result.getSupportsOcsp())) {
                    foundFalse = true;
                }
            }
            if (foundFalse) {
                return TestResults.FALSE;
            }
        }
        return TestResults.ERROR_DURING_TEST;
    }

    private TestResult getConclusiveSupportsStapling() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.isSupportsStapling()) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }

    private TestResult getConclusiveIncludesCertMessage() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.getStapledResponse() != null) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }

    private TestResult getConclusiveSupportsStapledNonce() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.getStapledResponse() != null
                        && result.getStapledResponse().getNonce() != null) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }

    private TestResult getConclusiveMustStaple() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.isMustStaple()) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }

    private TestResult getConclusiveSupportsNonce() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.isSupportsNonce()) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }

    private TestResult getConclusiveStapledResponseExpired() {
        if (certResults != null) {
            for (OcspCertificateResult result : certResults) {
                if (result.isStapledResponseExpired()) {
                    return TestResults.TRUE;
                }
            }
        }
        return TestResults.FALSE;
    }
}
