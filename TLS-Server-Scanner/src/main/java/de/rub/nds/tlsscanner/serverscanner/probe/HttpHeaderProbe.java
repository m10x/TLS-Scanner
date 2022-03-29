/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2022 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.probe;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.https.HttpsRequestMessage;
import de.rub.nds.tlsattacker.core.https.HttpsResponseMessage;
import de.rub.nds.tlsattacker.core.https.header.GenericHttpsHeader;
import de.rub.nds.tlsattacker.core.https.header.HostHeader;
import de.rub.nds.tlsattacker.core.https.header.HttpsHeader;
import de.rub.nds.tlsattacker.core.protocol.message.ChangeCipherSpecMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.protocol.message.FinishedMessage;
import de.rub.nds.tlsattacker.core.protocol.ProtocolMessage;
import de.rub.nds.tlsattacker.core.protocol.message.ServerHelloDoneMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.ParallelExecutor;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveTillAction;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendAction;
import de.rub.nds.tlsattacker.core.workflow.action.SendDynamicClientKeyExchangeAction;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.core.constants.TlsProbeType;
import de.rub.nds.scanner.core.constants.TestResult;
import de.rub.nds.tlsscanner.serverscanner.report.ServerReport;
import de.rub.nds.tlsscanner.serverscanner.probe.result.HttpHeaderResult;
import de.rub.nds.tlsscanner.core.probe.TlsProbe;
import de.rub.nds.tlsscanner.serverscanner.config.ServerScannerConfig;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class HttpHeaderProbe extends TlsProbe<ServerScannerConfig, ServerReport, HttpHeaderResult> {

    public HttpHeaderProbe(ServerScannerConfig scannerConfig, ParallelExecutor parallelExecutor) {
        super(parallelExecutor, TlsProbeType.HTTP_HEADER, scannerConfig);
    }

    @Override
    public HttpHeaderResult executeTest() {
        Config tlsConfig = getScannerConfig().createConfig();
        List<CipherSuite> cipherSuites = new LinkedList<>();
        cipherSuites.addAll(Arrays.asList(CipherSuite.values()));
        cipherSuites.remove(CipherSuite.TLS_FALLBACK_SCSV);
        cipherSuites.remove(CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV);
        tlsConfig.setQuickReceive(true);
        tlsConfig.setDefaultClientSupportedCipherSuites(cipherSuites);
        tlsConfig.setHighestProtocolVersion(ProtocolVersion.TLS12);
        tlsConfig.setEnforceSettings(false);
        tlsConfig.setEarlyStop(true);
        tlsConfig.setStopReceivingAfterFatal(true);
        tlsConfig.setStopActionsAfterFatal(true);
        tlsConfig.setHttpsParsingEnabled(true);
        tlsConfig.setWorkflowTraceType(WorkflowTraceType.HTTPS);
        tlsConfig.setStopActionsAfterIOException(true);
        // Don't send extensions if we are in SSLv2
        tlsConfig.setAddECPointFormatExtension(true);
        tlsConfig.setAddEllipticCurveExtension(true);
        tlsConfig.setAddSignatureAndHashAlgorithmsExtension(true);
        tlsConfig.setAddRenegotiationInfoExtension(true);

        List<NamedGroup> namedGroups = NamedGroup.getImplemented();
        namedGroups.remove(NamedGroup.ECDH_X25519);
        tlsConfig.setDefaultClientNamedGroups(namedGroups);
        WorkflowConfigurationFactory factory = new WorkflowConfigurationFactory(tlsConfig);
        WorkflowTrace trace = factory.createTlsEntryWorkflowTrace(tlsConfig.getDefaultClientConnection());
        trace.addTlsAction(new SendAction(new ClientHelloMessage(tlsConfig)));
        trace.addTlsAction(new ReceiveTillAction(new ServerHelloDoneMessage()));
        trace.addTlsAction(new SendDynamicClientKeyExchangeAction());
        trace.addTlsAction(new SendAction(new ChangeCipherSpecMessage(), new FinishedMessage()));
        trace.addTlsAction(new ReceiveAction(new ChangeCipherSpecMessage(), new FinishedMessage()));
        trace.addTlsAction(new SendAction(this.getHttpsRequest()));
        trace.addTlsAction(new ReceiveAction(new HttpsResponseMessage()));
        State state = new State(tlsConfig, trace);
        executeState(state);
        ReceivingAction action = trace.getLastReceivingAction();
        HttpsResponseMessage responseMessage = null;
        if (action.getReceivedMessages() != null) {
            for (ProtocolMessage message : action.getReceivedMessages()) {
                if (message instanceof HttpsResponseMessage) {
                    responseMessage = (HttpsResponseMessage) message;
                    break;
                }
            }
        }
        boolean speaksHttps = responseMessage != null;
        List<HttpsHeader> headerList;
        if (speaksHttps) {
            headerList = responseMessage.getHeader();
        } else {
            headerList = new LinkedList<>();
        }
        return new HttpHeaderResult(speaksHttps == true ? TestResult.TRUE : TestResult.FALSE, headerList);
    }

    // TODO OUTSOURCE
    protected HttpsRequestMessage getHttpsRequest() {
        HttpsRequestMessage httpsRequestMessage = new HttpsRequestMessage();
        httpsRequestMessage.setRequestPath("/");

        httpsRequestMessage.getHeader().add(new HostHeader());
        httpsRequestMessage.getHeader().add(new GenericHttpsHeader("Connection", "keep-alive"));
        httpsRequestMessage.getHeader().add(new GenericHttpsHeader("Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8"));
        httpsRequestMessage.getHeader()
            .add(new GenericHttpsHeader("Accept-Encoding", "compress, deflate, exi, gzip, br, bzip2, lzma, xz"));
        httpsRequestMessage.getHeader()
            .add(new GenericHttpsHeader("Accept-Language", "de-DE,de;q=0.8,en-US;q=0.6,en;q=0.4"));
        httpsRequestMessage.getHeader().add(new GenericHttpsHeader("Upgrade-Insecure-Requests", "1"));
        httpsRequestMessage.getHeader().add(new GenericHttpsHeader("User-Agent",
            "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3449.0 Safari/537.36"));
        return httpsRequestMessage;
    }

    @Override
    public boolean canBeExecuted(ServerReport report) {
        return true;
    }

    @Override
    public void adjustConfig(ServerReport report) {
    }

    @Override
    public HttpHeaderResult getCouldNotExecuteResult() {
        return new HttpHeaderResult(TestResult.COULD_NOT_TEST, null);
    }
}
