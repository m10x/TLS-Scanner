/**
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker.
 *
 * Copyright 2017-2019 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsscanner.report;

import de.rub.nds.tlsattacker.attacks.constants.DrownVulnerabilityType;
import de.rub.nds.tlsattacker.attacks.constants.EarlyCcsVulnerabilityType;
import static de.rub.nds.tlsattacker.attacks.constants.EarlyCcsVulnerabilityType.NOT_VULNERABLE;
import static de.rub.nds.tlsattacker.attacks.constants.EarlyCcsVulnerabilityType.VULN_EXPLOITABLE;
import static de.rub.nds.tlsattacker.attacks.constants.EarlyCcsVulnerabilityType.VULN_NOT_EXPLOITABLE;
import de.rub.nds.tlsattacker.attacks.padding.VectorResponse;
import de.rub.nds.tlsattacker.attacks.pkcs1.VectorFingerprintPair;
import de.rub.nds.tlsattacker.attacks.util.response.EqualityError;
import de.rub.nds.tlsattacker.core.constants.AlgorithmResolver;
import de.rub.nds.tlsattacker.core.constants.CipherSuite;
import de.rub.nds.tlsattacker.core.constants.CompressionMethod;
import de.rub.nds.tlsattacker.core.constants.ExtensionType;
import de.rub.nds.tlsattacker.core.constants.HashAlgorithm;
import de.rub.nds.tlsattacker.core.constants.NamedGroup;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.SignatureAndHashAlgorithm;
import de.rub.nds.tlsattacker.core.constants.TokenBindingKeyParameters;
import de.rub.nds.tlsattacker.core.constants.TokenBindingVersion;
import de.rub.nds.tlsattacker.core.https.header.HttpsHeader;
import de.rub.nds.tlsattacker.core.protocol.message.ProtocolMessage;
import de.rub.nds.tlsattacker.core.workflow.action.ReceivingAction;
import de.rub.nds.tlsscanner.constants.AnsiColors;
import de.rub.nds.tlsscanner.constants.CipherSuiteGrade;
import de.rub.nds.tlsscanner.probe.handshakeSimulation.SimulatedClientResult;
import de.rub.nds.tlsscanner.constants.ScannerDetail;
import de.rub.nds.tlsscanner.probe.certificate.CertificateChain;
import de.rub.nds.tlsscanner.probe.certificate.CertificateIssue;
import de.rub.nds.tlsscanner.probe.mac.CheckPattern;
import de.rub.nds.tlsscanner.probe.certificate.CertificateReport;
import de.rub.nds.tlsscanner.probe.padding.KnownPaddingOracleVulnerability;
import de.rub.nds.tlsscanner.probe.padding.PaddingOracleStrength;
import de.rub.nds.tlsscanner.report.after.prime.CommonDhValues;
import de.rub.nds.tlsscanner.probe.handshakeSimulation.ConnectionInsecure;
import de.rub.nds.tlsscanner.probe.handshakeSimulation.HandshakeFailureReasons;
import de.rub.nds.tlsscanner.rating.PropertyResultRatingInfluencer;
import de.rub.nds.tlsscanner.rating.PropertyResultRecommendation;
import de.rub.nds.tlsscanner.rating.Recommendation;
import de.rub.nds.tlsscanner.rating.ScoreReport;
import de.rub.nds.tlsscanner.rating.SiteReportRater;
import de.rub.nds.tlsscanner.rating.TestResult;
import de.rub.nds.tlsscanner.report.result.VersionSuiteListPair;
import de.rub.nds.tlsscanner.report.result.bleichenbacher.BleichenbacherTestResult;
import de.rub.nds.tlsscanner.report.result.hpkp.HpkpPin;
import de.rub.nds.tlsscanner.report.result.paddingoracle.PaddingOracleCipherSuiteFingerprint;
import de.rub.nds.tlsscanner.report.result.statistics.RandomEvaluationResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.JAXBException;

public class SiteReportPrinter {

    private static final Logger LOGGER = LogManager.getLogger(SiteReportPrinter.class.getName());

    private final SiteReport report;
    private final ScannerDetail detail;
    private int depth;

    private final String hsClientFormat = "%-28s";
    private final String hsVersionFormat = "%-14s";
    private final String hsCiphersuiteFormat = "%-52s";
    private final String hsForwardSecrecyFormat = "%-19s";
    private final String hsKeyLengthFormat = "%-17s";

    public SiteReportPrinter(SiteReport report, ScannerDetail detail) {
        this.report = report;
        this.detail = detail;
        depth = 0;
    }

    public String getFullReport() {
        StringBuilder builder = new StringBuilder();
        builder.append("Report for ");
        builder.append(report.getHost());
        builder.append("\n");
        if (report.getServerIsAlive() == Boolean.FALSE) {
            builder.append("Cannot reach the Server. Is it online?");
            return builder.toString();
        }
        if (report.getSupportsSslTls() == Boolean.FALSE) {
            builder.append("Server does not seem to support SSL / TLS on the scanned port");
            return builder.toString();
        }

        appendProtocolVersions(builder);
        appendCipherSuites(builder);
        appendExtensions(builder);
        appendCompressions(builder);
        appendIntolerances(builder);
        appendAttackVulnerabilities(builder);
        appendBleichenbacherResults(builder);
        appendPaddingOracleResults(builder);
        //appendGcm(builder);
        appendRfc(builder);
        appendCertificate(builder);
        appendSession(builder);
        appendRenegotiation(builder);
        appendHandshakeSimulation(builder);
        appendHttps(builder);
        appendRandom(builder);
        appendPublicKeyIssues(builder);
        appendScoringResults(builder);
        appendRecommendations(builder);
        for (PerformanceData data : report.getPerformanceList()) {
            LOGGER.debug("Type: " + data.getType() + "   Start: " + data.getStarttime() + "    Stop: " + data.getStoptime());
        }
        return builder.toString();
    }

    private StringBuilder appendHandshakeSimulation(StringBuilder builder) {
        if (report.getSimulatedClientList() != null) {
            appendHsNormal(builder);
            if (detail == ScannerDetail.DETAILED) {
                appendHandshakeSimulationTable(builder);
            } else if (detail == ScannerDetail.ALL) {
                appendHandshakeSimulationTable(builder);
                appendHandshakeSimulationDetails(builder);
            }
        }
        return builder;
    }

    private StringBuilder appendHsNormal(StringBuilder builder) {
        prettyAppendHeading(builder, "Handshake Simulation - Overview");
        prettyAppend(builder, "Tested Clients", Integer.toString(report.getSimulatedClientList().size()));
        builder.append("\n");
        String identifier;
        identifier = "Handshakes - Successful";
        if (report.getHandshakeSuccessfulCounter() == 0) {
            prettyAppendRed(builder, identifier, Integer.toString(report.getHandshakeSuccessfulCounter()));
        } else {
            prettyAppendGreen(builder, identifier, Integer.toString(report.getHandshakeSuccessfulCounter()));
        }
        identifier = "Handshakes - Failed";
        if (report.getHandshakeFailedCounter() == 0) {
            prettyAppendGreen(builder, identifier, Integer.toString(report.getHandshakeFailedCounter()));
        } else {
            prettyAppendRed(builder, identifier, Integer.toString(report.getHandshakeFailedCounter()));
        }
        builder.append("\n");
        return builder;
    }

    private StringBuilder appendHandshakeSimulationTable(StringBuilder builder) {
        prettyAppendHeading(builder, "Handshake Simulation");
        int counter = 0;
        appendHandshakeSimulationTableRowHeading(builder, "Client", "Version", "Ciphersuite", "Forward Secrecy", "Server Public Key");
        builder.append("\n");
        for (SimulatedClientResult simulatedClient : report.getSimulatedClientList()) {
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || simulatedClient.getTlsClientConfig().isDefaultVersion()) {
                if (simulatedClient.getHandshakeSuccessful()) {
                    appendHandshakeTableRowSuccessful(builder, simulatedClient);
                    counter++;
                } else {
                    appendHandshakeTableRowFailed(builder, simulatedClient);
                    counter++;
                }
            }
        }

        if (counter == 0) {
            prettyAppend(builder, "-");
        }
        return builder;
    }

    private StringBuilder appendHandshakeSimulationTableRowHeading(StringBuilder builder, String tlsClient, String tlsVersion,
            String ciphersuite, String forwardSecrecy, String keyLength) {
        builder.append(String.format(hsClientFormat, tlsClient));
        builder.append(String.format("| " + hsVersionFormat, tlsVersion));
        builder.append(String.format("| " + hsCiphersuiteFormat, ciphersuite));
        builder.append(String.format("| " + hsForwardSecrecyFormat, forwardSecrecy));
        builder.append(String.format("| " + hsKeyLengthFormat, keyLength));
        builder.append("\n");
        return builder;
    }

    private StringBuilder appendHandshakeTableRowSuccessful(StringBuilder builder, SimulatedClientResult simulatedClient) {
        String clientName = simulatedClient.getTlsClientConfig().getType() + ":" + simulatedClient.getTlsClientConfig().getVersion();
        builder.append(getClientColor(clientName, simulatedClient.getConnectionInsecure(), simulatedClient.getConnectionRfc7918Secure()));
        builder.append("| ").append(getProtocolVersionColor(simulatedClient.getSelectedProtocolVersion(), hsVersionFormat));
        builder.append("| ").append(getCipherSuiteColor(simulatedClient.getSelectedCiphersuite(), hsCiphersuiteFormat));
        builder.append("| ").append(getForwardSecrecyColor(simulatedClient.getForwardSecrecy()));
        builder.append("| ").append(getServerPublicKeyParameterColor(simulatedClient));
        builder.append("\n");
        return builder;
    }

    private StringBuilder appendHandshakeTableRowFailed(StringBuilder builder, SimulatedClientResult simulatedClient) {
        String clientName = simulatedClient.getTlsClientConfig().getType() + ":" + simulatedClient.getTlsClientConfig().getVersion();
        builder.append(String.format("%s", getRedString(clientName, hsClientFormat)));
        if (!simulatedClient.getFailReasons().isEmpty()) {
            for (HandshakeFailureReasons reason : simulatedClient.getFailReasons()) {
                builder.append(String.format("| %s", getRedString(reason.getReason(), hsVersionFormat)));
            }
        } else {
            ReceivingAction action = simulatedClient.getState().getWorkflowTrace().getLastReceivingAction();
            if (action.getReceivedMessages().isEmpty()) {
                builder.append(String.format("| %s", getRedString("Failed - No answer from server", "%s")));
            } else {
                StringBuilder messages = new StringBuilder();
                for (ProtocolMessage message : action.getReceivedMessages()) {
                    messages.append(message.toCompactString()).append(", ");
                }
                builder.append(String.format("| %s", getRedString("Failed - " + messages, "%s")));
            }
        }
        builder.append("\n");
        return builder;
    }

    private String getClientColor(String tlsClient, Boolean insecure, Boolean rfc7918Secure) {
        if (tlsClient != null) {
            if (insecure != null && insecure) {
                return getRedString(tlsClient, hsClientFormat);
            } else if (rfc7918Secure != null && rfc7918Secure) {
                return getGreenString(tlsClient, hsClientFormat);
            }
        } else {
            return "Unknown";
        }
        return getBlackString(tlsClient, hsClientFormat);
    }

    private String getProtocolVersionColor(ProtocolVersion version, String format) {
        if (version != null) {
            if (version.name().contains("13") || version.name().contains("12")) {
                return getGreenString(version.name(), format);
            } else if (version.name().contains("11") || version.name().contains("10")) {
                return getYellowString(version.name(), format);
            } else if (version.name().contains("SSL")) {
                return getRedString(version.name(), format);
            } else {
                return getBlackString(version.name(), format);
            }
        } else {
            return "Unknown";
        }
    }

    private String getCipherSuiteColor(CipherSuite suite, String format) {
        if (suite != null) {
            CipherSuiteGrade grade = CiphersuiteRater.getGrade(suite);
            switch (grade) {
                case GOOD:
                    return getGreenString(suite.name(), format);
                case LOW:
                    return getRedString(suite.name(), format);
                case MEDIUM:
                    return getYellowString(suite.name(), format);
                case NONE:
                    return getBlackString(suite.name(), format);
                default:
                    return getBlackString(suite.name(), format);
            }
        } else {
            return "Unknown";
        }
    }

    private String getForwardSecrecyColor(Boolean forwardSecrecy) {
        String fs;
        if (forwardSecrecy != null) {
            if (forwardSecrecy) {
                fs = getGreenString("Forward Secrecy", hsForwardSecrecyFormat);
            } else {
                fs = getRedString("No Forward Secrecy", hsForwardSecrecyFormat);
            }
        } else {
            fs = "Unknown";
        }
        return fs;
    }

    private String getServerPublicKeyParameterColor(SimulatedClientResult simulatedClient) {
        String pubKeyParam = getServerPublicKeyParameterToPrint(simulatedClient);
        if (simulatedClient.getServerPublicKeyParameter() != null) {
            if (simulatedClient.getInsecureReasons() != null) {
                for (String reason : simulatedClient.getInsecureReasons()) {
                    if (reason.contains(ConnectionInsecure.PUBLIC_KEY_SIZE_TOO_SMALL.getReason())) {
                        return getRedString(pubKeyParam, "%s");
                    }
                }
            }
            return getGreenString(pubKeyParam, "%s");
        }
        return getBlackString(pubKeyParam, "%s");
    }

    private String getServerPublicKeyParameterToPrint(SimulatedClientResult simulatedClient) {
        CipherSuite suite = simulatedClient.getSelectedCiphersuite();
        Integer param = simulatedClient.getServerPublicKeyParameter();
        if (suite != null && param != null) {
            if (AlgorithmResolver.getKeyExchangeAlgorithm(suite).isKeyExchangeRsa()) {
                return param + " bit - RSA";
            } else if (AlgorithmResolver.getKeyExchangeAlgorithm(suite).isKeyExchangeDh()) {
                return param + " bit - DH";
            } else if (AlgorithmResolver.getKeyExchangeAlgorithm(suite).isKeyExchangeEcdh()) {
                return param + " bit - ECDH - " + simulatedClient.getSelectedNamedGroup();
            }
        }
        return null;
    }

    private StringBuilder appendHandshakeSimulationDetails(StringBuilder builder) {
        prettyAppendHeading(builder, "Handshake Simulation - Details");
        for (SimulatedClientResult simulatedClient : report.getSimulatedClientList()) {
            prettyAppendHeading(builder, simulatedClient.getTlsClientConfig().getType() + ":" + simulatedClient.getTlsClientConfig().getVersion());
            prettyAppendGreenRed(builder, "Handshake Successful", simulatedClient.getHandshakeSuccessful());
            if (!simulatedClient.getHandshakeSuccessful()) {
                for (HandshakeFailureReasons failureReason : simulatedClient.getFailReasons()) {
                    prettyAppend(builder, "", getRedString(failureReason.getReason(), "%s"));
                }
            }
            builder.append("\n");
            if (simulatedClient.getConnectionInsecure() != null && simulatedClient.getConnectionInsecure()) {
                prettyAppendRedGreen(builder, "Connection Insecure", simulatedClient.getConnectionInsecure());
                for (String reason : simulatedClient.getInsecureReasons()) {
                    prettyAppend(builder, "", reason);
                }
            }
            if (simulatedClient.getConnectionRfc7918Secure() != null && simulatedClient.getConnectionRfc7918Secure()) {
                prettyAppendGreen(builder, "Connection Secure (RFC 7918)", simulatedClient.getConnectionRfc7918Secure().toString());
            } else {
                prettyAppend(builder, "Connection Secure (RFC 7918)", simulatedClient.getConnectionRfc7918Secure());
            }
            builder.append("\n");
            prettyAppend(builder, "Protocol Version Selected", getProtocolVersionColor(simulatedClient.getSelectedProtocolVersion(), "%s"));
            prettyAppend(builder, "Protocol Versions Client", simulatedClient.getSupportedVersionList().toString());
            prettyAppend(builder, "Protocol Versions Server", report.getVersions().toString());
            prettyAppendGreenRed(builder, "Protocol Version is highest", simulatedClient.getHighestPossibleProtocolVersionSeleceted());
            builder.append("\n");
            prettyAppend(builder, "Selected Ciphersuite", getCipherSuiteColor(simulatedClient.getSelectedCiphersuite(), "%s"));
            prettyAppendGreenRed(builder, "Forward Secrecy", simulatedClient.getForwardSecrecy());
            builder.append("\n");
            prettyAppend(builder, "Server Public Key", getServerPublicKeyParameterColor(simulatedClient));
            builder.append("\n");
            if (simulatedClient.getSelectedCompressionMethod() != null) {
                prettyAppend(builder, "Selected Compression Method", simulatedClient.getSelectedCompressionMethod().toString());
            } else {
                String tmp = null;
                prettyAppend(builder, "Selected Compression Method", tmp);
            }
            prettyAppend(builder, "Negotiated Extensions", simulatedClient.getNegotiatedExtensions());
            prettyAppend(builder, "Alpn Protocols", simulatedClient.getAlpnAnnouncedProtocols());
        }
        return builder;
    }

    private StringBuilder appendRfc(StringBuilder builder) {
        prettyAppendHeading(builder, "RFC");
        prettyAppendCheckPattern(builder, "Checks MAC (AppData)", report.getMacCheckPatternAppData());
        prettyAppendCheckPattern(builder, "Checks MAC (Finished)", report.getMacCheckPatternFinished());
        prettyAppendCheckPattern(builder, "Checks VerifyData", report.getVerifyCheckPattern());
        return builder;
    }

    private StringBuilder appendRenegotiation(StringBuilder builder) {
        prettyAppendHeading(builder, "Renegotioation & SCSV");
        prettyAppendYellowOnSuccess(builder, "Clientside Secure", report.getResult(AnalyzedProperty.SUPPORTS_CLIENT_SIDE_SECURE_RENEGOTIATION));
        prettyAppendRedOnSuccess(builder, "Clientside Insecure", report.getResult(AnalyzedProperty.SUPPORTS_CLIENT_SIDE_INSECURE_RENEGOTIATION));
        //prettyAppendRedOnFailure(builder, "SCSV Fallback", report.getTlsFallbackSCSVsupported());
        return builder;
    }

    private StringBuilder appendCertificate(StringBuilder builder) {
        if (report.getCertificateChain() != null) {
            CertificateChain chain = report.getCertificateChain();
            prettyAppendHeading(builder, "Certificate Chain");
            prettyAppendGreenYellow(builder, "Chain ordered", chain.getChainIsOrdered());
            prettyAppendRedGreen(builder, "Contains Trust Anchor", chain.getContainsTrustAnchor());
            prettyAppendGreenRed(builder, "Generally Trusted", chain.getGenerallyTrusted());
            if (chain.getCertificateIssues().size() > 0) {
                prettyAppendSubheading(builder, "Certificate Issues");
                for (CertificateIssue issue : chain.getCertificateIssues()) {
                    prettyAppendRed(builder, issue.getHumanReadable());
                }
            }
            if (!chain.getCertificateReportList().isEmpty()) {
                for (int i = 0; i < chain.getCertificateReportList().size(); i++) {
                    CertificateReport report = chain.getCertificateReportList().get(i);
                    prettyAppendSubheading(builder, "Certificate #" + (i + 1));

                    if (report.getSubject() != null) {
                        prettyAppend(builder, "Subject", report.getSubject());
                    }

                    if (report.getIssuer() != null) {
                        prettyAppend(builder, "Issuer", report.getIssuer());
                    }
                    if (report.getValidFrom() != null) {
                        if (report.getValidFrom().before(new Date())) {
                            prettyAppendGreen(builder, "Valid From", report.getValidFrom().toString());
                        } else {
                            prettyAppendRed(builder, "Valid From", report.getValidFrom().toString() + " - NOT YET VALID");
                        }
                    }
                    if (report.getValidTo() != null) {
                        if (report.getValidTo().after(new Date())) {
                            prettyAppendGreen(builder, "Valid Till", report.getValidTo().toString());
                        } else {
                            prettyAppendRed(builder, "Valid Till", report.getValidTo().toString() + " - EXPIRED");
                        }

                    }
                    if (report.getValidFrom() != null && report.getValidTo() != null && report.getValidTo().after(new Date())) {
                        long time = report.getValidTo().getTime() - System.currentTimeMillis();
                        long days = TimeUnit.MILLISECONDS.toDays(time);
                        if (days < 1) {
                            prettyAppendRed(builder, "Expires in", "<1 day! This certificate expires very soon");
                        } else if (days < 3) {
                            prettyAppendRed(builder, "Expires in", days + " days! This certificate expires soon");
                        } else if (days < 14) {
                            prettyAppendYellow(builder, "Expires in", days + " days. This certificate expires soon");
                        } else if (days < 31) {
                            prettyAppend(builder, "Expires in", days + " days.");
                        } else if (days < 730) {
                            prettyAppendGreen(builder, "Expires in", days + " days.");
                        } else if (Objects.equals(report.getLeafCertificate(), Boolean.TRUE)) {
                            prettyAppendRed(builder, "Expires in", days + " days. This is usually to long for a leaf Certificate");
                        } else {
                            prettyAppendGreen(builder, "Expires in", days / 365 + " years");
                        }
                    }
                    if (report.getPublicKey() != null) {
                        prettyAppend(builder, "PublicKey", report.getPublicKey().toString());
                    }
                    if (report.getWeakDebianKey() != null) {
                        prettyAppendRedGreen(builder, "Weak Debian Key", report.getWeakDebianKey());
                    }
                    if (report.getSignatureAndHashAlgorithm() != null) {
                        prettyAppend(builder, "Signature Algorithm", report.getSignatureAndHashAlgorithm().getSignatureAlgorithm().name());
                    }
                    if (report.getSignatureAndHashAlgorithm() != null) {
                        if (report.getSignatureAndHashAlgorithm().getHashAlgorithm() == HashAlgorithm.SHA1 || report.getSignatureAndHashAlgorithm().getHashAlgorithm() == HashAlgorithm.MD5) {
                            if (!report.isTrustAnchor() && !report.getSelfSigned()) {
                                prettyAppendRed(builder, "Hash Algorithm", report.getSignatureAndHashAlgorithm().getHashAlgorithm().name());
                            } else {
                                prettyAppendYellow(builder, "Hash Algorithm", report.getSignatureAndHashAlgorithm().getHashAlgorithm().name() + " - Not critical");
                            }
                        } else {
                            prettyAppendGreen(builder, "Hash Algorithm", report.getSignatureAndHashAlgorithm().getHashAlgorithm().name());
                        }
                    }
                    if (report.getExtendedValidation() != null) {
                        prettyAppendGreenOnSuccess(builder, "Extended Validation", report.getExtendedValidation());
                    }
                    if (report.getCertificateTransparency() != null) {
                        prettyAppendGreenYellow(builder, "Certificate Transparency", report.getCertificateTransparency());
                    }

                    if (report.getCrlSupported() != null) {
                        prettyAppendGreenOnSuccess(builder, "CRL Supported", report.getCrlSupported());
                    }
                    if (report.getOcspSupported() != null) {
                        prettyAppendGreenYellow(builder, "OCSP Supported", report.getOcspSupported());
                    }
                    if (report.getOcspMustStaple() != null) {
                        prettyAppend(builder, "OCSP must Staple", report.getOcspMustStaple());
                    }
                    if (report.getRevoked() != null) {
                        prettyAppendRedGreen(builder, "RevocationStatus", report.getRevoked());
                    }
                    if (report.getDnsCAA() != null) {
                        prettyAppendGreenOnSuccess(builder, "DNS CCA", report.getDnsCAA());
                    }
                    if (report.getRocaVulnerable() != null) {
                        prettyAppendRedGreen(builder, "ROCA (simple)", report.getRocaVulnerable());
                    } else {
                        builder.append("ROCA (simple): not tested");
                    }
                    prettyAppend(builder, "Fingerprint (SHA256)", report.getSHA256Fingerprint());

                }
            }
        }
        return builder;
    }

    private StringBuilder appendSession(StringBuilder builder) {
        prettyAppendHeading(builder, "Session");
        prettyAppendGreenYellow(builder, "Supports Session resumption", report.getResult(AnalyzedProperty.SUPPORTS_SESSION_IDS));
        prettyAppendGreenYellow(builder, "Supports Session Tickets", report.getResult(AnalyzedProperty.SUPPORTS_SESSION_TICKETS));
        //prettyAppend(builder, "Session Ticket Hint", report.getSessionTicketLengthHint());
        //prettyAppendYellowOnFailure(builder, "Session Ticket Rotation", report.getSessionTicketGetsRotated());
        //prettyAppendRedOnFailure(builder, "Ticketbleed", report.getVulnerableTicketBleed());
        return builder;
    }

    private StringBuilder appendGcm(StringBuilder builder) {
        prettyAppendHeading(builder, "GCM");
        prettyAppendRedOnFailure(builder, "GCM Nonce reuse", report.getResult(AnalyzedProperty.REUSES_GCM_NONCES));
        if (null == report.getGcmPattern()) {
            prettyAppend(builder, "GCM Pattern", (String) null);
        } else {
            switch (report.getGcmPattern()) {
                case AKWARD:
                    prettyAppendYellow(builder, addIndentations("GCM Pattern") + report.getGcmPattern().name());
                    break;
                case INCREMENTING:
                case RANDOM:
                    prettyAppendGreen(builder, addIndentations("GCM Pattern") + report.getGcmPattern().name());
                    break;
                case REPEATING:
                    prettyAppendRed(builder, addIndentations("GCM Pattern") + report.getGcmPattern().name());
                    break;
                default:
                    prettyAppend(builder, addIndentations("GCM Pattern") + report.getGcmPattern().name());
                    break;
            }
        }
        prettyAppendRedOnFailure(builder, "GCM Check", report.getResult(AnalyzedProperty.MISSES_GCM_CHECKS));
        return builder;
    }

    private StringBuilder appendIntolerances(StringBuilder builder) {
        prettyAppendHeading(builder, "Common Bugs [EXPERIMENTAL]");
        prettyAppendRedGreen(builder, "Version Intolerant", report.getResult(AnalyzedProperty.HAS_VERSION_INTOLERANCE));
        prettyAppendRedGreen(builder, "Ciphersuite Intolerant", report.getResult(AnalyzedProperty.HAS_CIPHERSUITE_INTOLERANCE));
        prettyAppendRedGreen(builder, "Extension Intolerant", report.getResult(AnalyzedProperty.HAS_EXTENSION_INTOLERANCE));
        prettyAppendRedGreen(builder, "CS Length Intolerant (>512 Byte)", report.getResult(AnalyzedProperty.HAS_CIPHERSUITE_LENGTH_INTOLERANCE));
        prettyAppendRedGreen(builder, "Compression Intolerant", report.getResult(AnalyzedProperty.HAS_COMPRESSION_INTOLERANCE));
        prettyAppendRedGreen(builder, "ALPN Intolerant", report.getResult(AnalyzedProperty.HAS_ALPN_INTOLERANCE));
        prettyAppendRedGreen(builder, "CH Length Intolerant", report.getResult(AnalyzedProperty.HAS_CLIENT_HELLO_LENGTH_INTOLERANCE));
        prettyAppendRedGreen(builder, "NamedGroup Intolerant", report.getResult(AnalyzedProperty.HAS_NAMED_GROUP_INTOLERANCE));
        prettyAppendRedGreen(builder, "Empty last Extension Intolerant", report.getResult(AnalyzedProperty.HAS_EMPTY_LAST_EXTENSION_INTOLERANCE));
        prettyAppendRedGreen(builder, "SigHashAlgo Intolerant", report.getResult(AnalyzedProperty.HAS_SIG_HASH_ALGORITHM_INTOLERANCE));
        prettyAppendRedGreen(builder, "Big ClientHello Intolerant", report.getResult(AnalyzedProperty.HAS_BIG_CLIENT_HELLO_INTOLERANCE));
        prettyAppendRedGreen(builder, "2nd Ciphersuite Byte Bug", report.getResult(AnalyzedProperty.HAS_SECOND_CIPHERSUITE_BYTE_BUG));
        prettyAppendRedGreen(builder, "Ignores offered Ciphersuites", report.getResult(AnalyzedProperty.IGNORES_OFFERED_CIPHERSUITES));
        prettyAppendRedGreen(builder, "Reflects offered Ciphersuites", report.getResult(AnalyzedProperty.REFLECTS_OFFERED_CIPHERSUITES));
        prettyAppendRedGreen(builder, "Ignores offered NamedGroups", report.getResult(AnalyzedProperty.IGNORES_OFFERED_NAMED_GROUPS));
        prettyAppendRedGreen(builder, "Ignores offered SigHashAlgos", report.getResult(AnalyzedProperty.IGNORES_OFFERED_SIG_HASH_ALGOS));
        return builder;
    }

    private StringBuilder appendAttackVulnerabilities(StringBuilder builder) {
        prettyAppendHeading(builder, "Attack Vulnerabilities");
        if (report.getKnownVulnerability() == null) {
            prettyAppendRedGreen(builder, "Padding Oracle", report.getResult(AnalyzedProperty.VULNERABLE_TO_PADDING_ORACLE));
        } else {
            prettyAppendRed(builder, "Padding Oracle", "true - " + report.getKnownVulnerability().getShortName());
        }
        prettyAppendRedGreen(builder, "Bleichenbacher", report.getResult(AnalyzedProperty.VULNERABLE_TO_BLEICHENBACHER));
        prettyAppendRedGreen(builder, "CRIME", report.getResult(AnalyzedProperty.VULNERABLE_TO_CRIME));
        prettyAppendRedGreen(builder, "Breach", report.getResult(AnalyzedProperty.VULNERABLE_TO_BREACH));
        prettyAppendRedGreen(builder, "Invalid Curve", report.getResult(AnalyzedProperty.VULNERABLE_TO_INVALID_CURVE));
        prettyAppendRedGreen(builder, "Invalid Curve (ephemeral)", report.getResult(AnalyzedProperty.VULNERABLE_TO_INVALID_CURVE_EPHEMERAL));
        prettyAppendRedGreen(builder, "SSL Poodle", report.getResult(AnalyzedProperty.VULNERABLE_TO_POODLE));
        prettyAppendRedGreen(builder, "TLS Poodle", report.getResult(AnalyzedProperty.VULNERABLE_TO_TLS_POODLE));
        prettyAppendRedGreen(builder, "Logjam", report.getResult(AnalyzedProperty.VULNERABLE_TO_LOGJAM));
        prettyAppendRedGreen(builder, "Sweet 32", report.getResult(AnalyzedProperty.VULNERABLE_TO_SWEET_32));
        prettyAppendRedGreen(builder, "DROWN", report.getResult(AnalyzedProperty.VULNERABLE_TO_DROWN));
        prettyAppendRedGreen(builder, "Heartbleed", report.getResult(AnalyzedProperty.VULNERABLE_TO_HEARTBLEED));
        prettyAppendRedGreen(builder, "EarlyCcs", report.getResult(AnalyzedProperty.VULNERABLE_TO_EARLY_CCS));
        return builder;
    }

    private StringBuilder appendPaddingOracleResults(StringBuilder builder) {
        if (Objects.equals(report.getResult(AnalyzedProperty.VULNERABLE_TO_PADDING_ORACLE), TestResult.TRUE)) {
            prettyAppendHeading(builder, "PaddingOracle Details");

            if (report.getKnownVulnerability() != null) {
                KnownPaddingOracleVulnerability knownVulnerability = report.getKnownVulnerability();
                prettyAppendRed(builder, "Identification", knownVulnerability.getLongName());
                prettyAppendRed(builder, "CVE", knownVulnerability.getCve());
                if (knownVulnerability.getStrength() != PaddingOracleStrength.WEAK) {
                    prettyAppendRed(builder, "Strength", "" + knownVulnerability.getStrength());
                } else {
                    prettyAppendYellow(builder, "Strength", "" + knownVulnerability.getStrength());
                }
                if (knownVulnerability.isObservable()) {
                    prettyAppendRed(builder, "Observable", "" + knownVulnerability.isObservable());
                } else {
                    prettyAppendYellow(builder, "Observable", "" + knownVulnerability.isObservable());
                }
                prettyAppend(builder, "\n");
                prettyAppend(builder, knownVulnerability.getDescription());
                prettyAppendHeading(builder, "Affected Products");

                for (String s : knownVulnerability.getAffectedProducts()) {
                    prettyAppendYellow(builder, s);
                }
                prettyAppend(builder, "");
                prettyAppend(builder, "If your tested software/hardware is not in this list, please let us know so we can add it here.");
            } else {
                prettyAppendYellow(builder, "Identification", "Could not identify vulnerability. Please contact us if you know which software/hardware is generating this behavior.");
            }
        }
        prettyAppendHeading(builder, "PaddingOracle Responsemap");
        if (report.getPaddingOracleTestResultList() == null || report.getPaddingOracleTestResultList().isEmpty()) {
            prettyAppend(builder, "No Testresults");
        } else {
            for (PaddingOracleCipherSuiteFingerprint testResult : report.getPaddingOracleTestResultList()) {
                String resultString = "" + padToLength(testResult.getSuite().name(), 40) + " - " + testResult.getVersion();
                if (testResult.isHasScanningError()) {
                    prettyAppendYellow(builder, resultString + "\t # Error during Scan");
                } else if (Objects.equals(testResult.getVulnerable(), Boolean.TRUE)) {
                    prettyAppendRed(builder, resultString + "\t - " + testResult.getEqualityError() + "  VULNERABLE");
                } else if (testResult.isShakyScans()) {
                    prettyAppendYellow(builder, resultString + "\t - Non Deterministic");
                } else if (Objects.equals(testResult.getVulnerable(), Boolean.FALSE)) {
                    prettyAppendGreen(builder, resultString + "\t - No Behavior Difference");
                } else {
                    prettyAppendYellow(builder, resultString + "\t # Unknown");
                }

                if ((detail == ScannerDetail.DETAILED && Objects.equals(testResult.getVulnerable(), Boolean.TRUE)) || detail == ScannerDetail.ALL) {
                    if (testResult.getEqualityError() != EqualityError.NONE || detail == ScannerDetail.ALL) {
                        prettyAppendYellow(builder, "Response Map");
                        appendPaddingOracleResponseMapList(builder, testResult.getResponseMapList());
                    }
                }
            }

        }
        return builder;
    }

    private StringBuilder appendPaddingOracleResponseMapList(StringBuilder builder, List<List<VectorResponse>> responseMapList) {
        if (responseMapList != null && !responseMapList.isEmpty()) {
            for (int vectorIndex = 0; vectorIndex < responseMapList.get(0).size(); vectorIndex++) {
                VectorResponse vectorResponse = responseMapList.get(0).get(vectorIndex);
                if (vectorResponse.isErrorDuringHandshake()) {
                    prettyAppendRed(builder, padToLength("\t" + vectorResponse.getPaddingVector().getName(), 40) + "ERROR");
                } else if (vectorResponse.isMissingEquivalent()) {
                    prettyAppendRed(builder, padToLength("\t" + vectorResponse.getPaddingVector().getName(), 40) + vectorResponse.getFingerprint().toHumanReadable());
                } else if (vectorResponse.isShaky()) {
                    prettyAppendYellow(builder, padToLength("\t" + vectorResponse.getPaddingVector().getName(), 40) + vectorResponse.getFingerprint().toHumanReadable());

                    for (int mapIndex = 1; mapIndex < responseMapList.size(); mapIndex++) {
                        VectorResponse shakyVectorResponse = responseMapList.get(mapIndex).get(vectorIndex);
                        if (shakyVectorResponse.getFingerprint() == null) {
                            prettyAppendYellow(builder, "\t" + padToLength("", 39) + "null");
                        } else {
                            prettyAppendYellow(builder, "\t" + padToLength("", 39) + shakyVectorResponse.getFingerprint().toHumanReadable());
                        }
                    }
                } else {
                    prettyAppend(builder, padToLength("\t" + vectorResponse.getPaddingVector().getName(), 40) + vectorResponse.getFingerprint().toHumanReadable());
                    if (detail.isGreaterEqualTo(ScannerDetail.ALL)) {
                        for (int mapIndex = 1; mapIndex < responseMapList.size(); mapIndex++) {
                            VectorResponse tempVectorResponse = responseMapList.get(mapIndex).get(vectorIndex);
                            if (tempVectorResponse == null || tempVectorResponse.getFingerprint() == null) {
                                prettyAppendRed(builder, "\t" + padToLength("", 39) + "Missing");
                            } else {
                                if (tempVectorResponse.isShaky()) {
                                    prettyAppendYellow(builder, "\t" + padToLength("", 39) + tempVectorResponse.getFingerprint().toHumanReadable());
                                } else {
                                    prettyAppend(builder, "\t" + padToLength("", 39) + tempVectorResponse.getFingerprint().toHumanReadable());
                                }
                            }
                        }
                    }
                }
            }
        } else {
            prettyAppend(builder, "\tNULL");
        }
        return builder;
    }

    private StringBuilder appendBleichenbacherResults(StringBuilder builder) {
        prettyAppendHeading(builder, "Bleichenbacher Details");
        if (report.getBleichenbacherTestResultList() == null || report.getBleichenbacherTestResultList().isEmpty()) {
            prettyAppend(builder, "No Testresults");
        } else {
            for (BleichenbacherTestResult testResult : report.getBleichenbacherTestResultList()) {
                String resultString = "" + padToLength(testResult.getWorkflowType().name(), 40);
                if (testResult.getVulnerable() == Boolean.TRUE) {
                    prettyAppendRed(builder, resultString + "\t - " + testResult.getEqualityError() + "  VULNERABLE");
                } else if (testResult.getVulnerable() == Boolean.FALSE) {
                    prettyAppendGreen(builder, resultString + "\t - No Behavior Difference");
                } else {
                    prettyAppendYellow(builder, resultString + "\t # Error during Scan");
                }

                if (detail == ScannerDetail.DETAILED || detail == ScannerDetail.ALL) {
                    if (testResult.getEqualityError() != EqualityError.NONE || detail == ScannerDetail.ALL) {
                        prettyAppendYellow(builder, "Response Map");
                        if (testResult.getVectorFingerPrintPairList() != null && !testResult.getVectorFingerPrintPairList().isEmpty()) {
                            for (VectorFingerprintPair vectorFingerPrintPair : testResult.getVectorFingerPrintPairList()) {
                                prettyAppend(builder, padToLength("\t" + vectorFingerPrintPair.getVector().getDescription(), 60) + vectorFingerPrintPair.getFingerprint().toHumanReadable());
                            }
                        } else {
                            prettyAppend(builder, "\tNULL");
                        }
                    }
                }
            }
        }
        return builder;
    }

    private StringBuilder appendCipherSuites(StringBuilder builder) {
        if (report.getCipherSuites() != null) {
            prettyAppendHeading(builder, "Supported Ciphersuites");
            for (CipherSuite suite : report.getCipherSuites()) {
                builder.append(getCipherSuiteColor(suite, "%s")).append("\n");
            }
            if (report.getSupportedTls13CipherSuites() != null) {
                for (CipherSuite suite : report.getSupportedTls13CipherSuites()) {
                    builder.append(getCipherSuiteColor(suite, "%s")).append("\n");
                }
            }

            for (VersionSuiteListPair versionSuitePair : report.getVersionSuitePairs()) {
                prettyAppendHeading(builder, "Supported in " + versionSuitePair.getVersion());
                for (CipherSuite suite : versionSuitePair.getCiphersuiteList()) {
                    builder.append(getCipherSuiteColor(suite, "%s")).append("\n");
                }
            }
            if (report.getSupportedTls13CipherSuites() != null && report.getSupportedTls13CipherSuites().size() > 0) {
                prettyAppendHeading(builder, "Supported in TLS 1.3");
                for (CipherSuite suite : report.getSupportedTls13CipherSuites()) {
                    builder.append(getCipherSuiteColor(suite, "%s")).append("\n");
                }
            }
            prettyAppendHeading(builder, "Symmetric Supported");
            prettyAppendRedOnSuccess(builder, "Null", report.getResult(AnalyzedProperty.SUPPORTS_NULL_CIPHERS));
            prettyAppendRedOnSuccess(builder, "Export", report.getResult(AnalyzedProperty.SUPPORTS_EXPORT));
            prettyAppendRedOnSuccess(builder, "Anon", report.getResult(AnalyzedProperty.SUPPORTS_ANON));
            prettyAppendRedOnSuccess(builder, "DES", report.getResult(AnalyzedProperty.SUPPORTS_DES));
            prettyAppendYellowOnSuccess(builder, "SEED", report.getResult(AnalyzedProperty.SUPPORTS_SEED));
            prettyAppendYellowOnSuccess(builder, "IDEA", report.getResult(AnalyzedProperty.SUPPORTS_IDEA));
            prettyAppendRedOnSuccess(builder, "RC2", report.getResult(AnalyzedProperty.SUPPORTS_RC2));
            prettyAppendRedOnSuccess(builder, "RC4", report.getResult(AnalyzedProperty.SUPPORTS_RC4));
            prettyAppendYellowOnSuccess(builder, "3DES", report.getResult(AnalyzedProperty.SUPPORTS_3DES));
            prettyAppend(builder, "AES", report.getResult(AnalyzedProperty.SUPPORTS_AES));
            prettyAppend(builder, "CAMELLIA", report.getResult(AnalyzedProperty.SUPPORTS_CAMELLIA));
            prettyAppend(builder, "ARIA", report.getResult(AnalyzedProperty.SUPPORTS_ARIA));
            prettyAppendGreenOnSuccess(builder, "CHACHA20 POLY1305", report.getResult(AnalyzedProperty.SUPPORTS_CHACHA));

            prettyAppendHeading(builder, "KeyExchange Supported");
            prettyAppendYellowOnSuccess(builder, "RSA", report.getResult(AnalyzedProperty.SUPPORTS_RSA));
            prettyAppend(builder, "DH", report.getResult(AnalyzedProperty.SUPPORTS_DH));
            prettyAppend(builder, "ECDH", report.getResult(AnalyzedProperty.SUPPORTS_ECDH));
            prettyAppendYellowOnSuccess(builder, "GOST", report.getResult(AnalyzedProperty.SUPPORTS_GOST));
            //prettyAppend(builder, "SRP", report.getSupportsSrp());
            prettyAppend(builder, "Kerberos", report.getResult(AnalyzedProperty.SUPPORTS_KERBEROS));
            prettyAppend(builder, "Plain PSK", report.getResult(AnalyzedProperty.SUPPORTS_PSK_PLAIN));
            prettyAppend(builder, "PSK RSA", report.getResult(AnalyzedProperty.SUPPORTS_PSK_RSA));
            prettyAppend(builder, "PSK DHE", report.getResult(AnalyzedProperty.SUPPORTS_PSK_DHE));
            prettyAppend(builder, "PSK ECDHE", report.getResult(AnalyzedProperty.SUPPORTS_PSK_ECDHE));
            prettyAppendYellowOnSuccess(builder, "Fortezza", report.getResult(AnalyzedProperty.SUPPORTS_FORTEZZA));
            prettyAppendGreenOnSuccess(builder, "New Hope", report.getResult(AnalyzedProperty.SUPPORTS_NEWHOPE));
            prettyAppendGreenOnSuccess(builder, "ECMQV", report.getResult(AnalyzedProperty.SUPPORTS_ECMQV));

            prettyAppendHeading(builder, "Perfect Forward Secrecy");
            prettyAppendGreenOnSuccess(builder, "Supports PFS", report.getResult(AnalyzedProperty.SUPPORTS_PFS));
            prettyAppendGreenOnSuccess(builder, "Prefers PFS", report.getResult(AnalyzedProperty.PREFERS_PFS));
            prettyAppendGreenOnSuccess(builder, "Supports Only PFS", report.getResult(AnalyzedProperty.SUPPORTS_ONLY_PFS));

            prettyAppendHeading(builder, "Cipher Types Supports");
            prettyAppend(builder, "Stream", report.getResult(AnalyzedProperty.SUPPORTS_STREAM_CIPHERS));
            prettyAppend(builder, "Block", report.getResult(AnalyzedProperty.SUPPORTS_BLOCK_CIPHERS));
            prettyAppendGreenYellow(builder, "AEAD", report.getResult(AnalyzedProperty.SUPPORTS_AEAD));

            prettyAppendHeading(builder, "Ciphersuite General");
            prettyAppendGreenRed(builder, "Enforces Ciphersuite ordering", report.getResult(AnalyzedProperty.ENFOCRES_CS_ORDERING));
        }
        return builder;
    }

    private StringBuilder appendProtocolVersions(StringBuilder builder) {
        if (report.getVersions() != null) {
            prettyAppendHeading(builder, "Versions");
            prettyAppendRedGreen(builder, "SSL 2.0", report.getResult(AnalyzedProperty.SUPPORTS_SSL_2));
            prettyAppendRedGreen(builder, "SSL 3.0", report.getResult(AnalyzedProperty.SUPPORTS_SSL_3));
            prettyAppendYellowOnFailure(builder, "TLS 1.0", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_0));
            prettyAppendYellowOnFailure(builder, "TLS 1.1", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_1));
            prettyAppendRedOnFailure(builder, "TLS 1.2", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_2));
            prettyAppendGreenOnSuccess(builder, "TLS 1.3", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3));
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_14) == TestResult.TRUE) {
                prettyAppendYellowOnSuccess(builder, "TLS 1.3 Draft 14", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_14));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_15) == TestResult.TRUE) {
                prettyAppendYellowOnSuccess(builder, "TLS 1.3 Draft 15", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_15));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_16) == TestResult.TRUE) {
                prettyAppendYellowOnSuccess(builder, "TLS 1.3 Draft 16", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_16));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_17) == TestResult.TRUE) {
                prettyAppendYellowOnSuccess(builder, "TLS 1.3 Draft 17", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_17));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_18) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 18", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_18));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_19) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 19", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_19));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_20) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 20", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_20));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_21) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 21", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_21));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_22) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 22", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_22));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_23) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 23", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_23));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_24) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 24", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_24));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_25) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 25", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_25));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_26) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 26", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_26));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_27) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 27", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_27));
            }
            if (detail.isGreaterEqualTo(ScannerDetail.DETAILED) || report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_28) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "TLS 1.3 Draft 28", report.getResult(AnalyzedProperty.SUPPORTS_TLS_1_3_DRAFT_28));
            }
        }
        return builder;
    }

    private StringBuilder appendHttps(StringBuilder builder) {
        if (report.getResult(AnalyzedProperty.SUPPORTS_HTTPS) == TestResult.TRUE) {
            prettyAppendHeading(builder, "HSTS");
            if (report.getResult(AnalyzedProperty.SUPPORTS_HSTS) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "HSTS", report.getResult(AnalyzedProperty.SUPPORTS_HSTS));
                prettyAppendGreenOnSuccess(builder, "HSTS Preloading", report.getResult(AnalyzedProperty.SUPPORTS_HSTS_PRELOADING));
                prettyAppend(builder, "max-age (seconds)", (long) report.getHstsMaxAge());
            } else {
                prettyAppend(builder, "Not supported");
            }
            prettyAppendHeading(builder, "HPKP");
            if (report.getResult(AnalyzedProperty.SUPPORTS_HPKP) == TestResult.TRUE || report.getResult(AnalyzedProperty.SUPPORTS_HPKP_REPORTING) == TestResult.TRUE) {
                prettyAppendGreenOnSuccess(builder, "HPKP", report.getResult(AnalyzedProperty.SUPPORTS_HPKP));
                prettyAppendGreenOnSuccess(builder, "HPKP (report only)", report.getResult(AnalyzedProperty.SUPPORTS_HPKP_REPORTING));
                prettyAppend(builder, "max-age (seconds)", (long) report.getHpkpMaxAge());
                if (report.getNormalHpkpPins().size() > 0) {
                    prettyAppend(builder, "");
                    prettyAppendGreen(builder, "HPKP-Pins:");
                    for (HpkpPin pin : report.getNormalHpkpPins()) {
                        prettyAppend(builder, pin.toString());
                    }
                }
                if (report.getReportOnlyHpkpPins().size() > 0) {
                    prettyAppend(builder, "");
                    prettyAppendGreen(builder, "Report Only HPKP-Pins:");
                    for (HpkpPin pin : report.getReportOnlyHpkpPins()) {
                        prettyAppend(builder, pin.toString());
                    }
                }

            } else {
                prettyAppend(builder, "Not supported");
            }
            prettyAppendHeading(builder, "HTTPS Response Header");
            for (HttpsHeader header : report.getHeaderList()) {
                prettyAppend(builder, header.getHeaderName().getValue() + ":" + header.getHeaderValue().getValue());
            }
        }
        return builder;
    }

    private StringBuilder appendExtensions(StringBuilder builder) {
        if (report.getSupportedExtensions() != null) {
            prettyAppendHeading(builder, "Supported Extensions");
            for (ExtensionType type : report.getSupportedExtensions()) {
                builder.append(type.name()).append("\n");
            }
        }
        prettyAppendHeading(builder, "Extensions");
        prettyAppendGreenRed(builder, "Secure Renegotiation", report.getResult(AnalyzedProperty.SUPPORTS_SECURE_RENEGOTIATION_EXTENSION));
        prettyAppendGreenOnSuccess(builder, "Extended Master Secret", report.getResult(AnalyzedProperty.SUPPORTS_EXTENDED_MASTER_SECRET));
        prettyAppendGreenOnSuccess(builder, "Encrypt Then Mac", report.getResult(AnalyzedProperty.SUPPORTS_ENCRYPT_THEN_MAC));
        prettyAppendGreenOnSuccess(builder, "Tokenbinding", report.getResult(AnalyzedProperty.SUPPORTS_TOKENBINDING));

        if (report.getResult(AnalyzedProperty.SUPPORTS_TOKENBINDING) == TestResult.TRUE) {
            prettyAppendHeading(builder, "Tokenbinding Version");
            for (TokenBindingVersion version : report.getSupportedTokenBindingVersion()) {
                builder.append(version.toString()).append("\n");
            }

            prettyAppendHeading(builder, "Tokenbinding Key Parameters");
            for (TokenBindingKeyParameters keyParameter : report.getSupportedTokenBindingKeyParameters()) {
                builder.append(keyParameter.toString()).append("\n");
            }
        }
        appendTls13Groups(builder);
        appendCurves(builder);
        appendSignatureAndHashAlgorithms(builder);
        return builder;
    }

    private void appendRandom(StringBuilder builder) {
        prettyAppendHeading(builder, "Nonce");
        prettyAppendRandom(builder, "Random", report.getRandomEvaluationResult());
    }

    private void appendPublicKeyIssues(StringBuilder builder) {
        prettyAppendHeading(builder, "PublicKey Parameter");
        prettyAppendYellowGreen(builder, "EC PublicKey reuse", report.getResult(AnalyzedProperty.REUSES_EC_PUBLICKEY));
        prettyAppendYellowGreen(builder, "DH PublicKey reuse", report.getResult(AnalyzedProperty.REUSES_DH_PUBLICKEY));
        prettyAppendYellowGreen(builder, "Uses Common DH Primes", report.getResult(AnalyzedProperty.SUPPORTS_COMMON_DH_PRIMES));
        if (report.getUsedCommonDhValueList() != null && report.getUsedCommonDhValueList().size() != 0) {
            for (CommonDhValues value : report.getUsedCommonDhValueList()) {
                prettyAppendRed(builder, "\t" + value.getName());
            }
        }
        prettyAppendGreenRed(builder, "Uses only prime moduli", report.getResult(AnalyzedProperty.SUPPORTS_ONLY_PRIME_MODULI));
        prettyAppendGreenRed(builder, "Uses only safe-prime moduli", report.getResult(AnalyzedProperty.SUPPORTS_ONLY_SAFEPRIME_MODULI));
        if (report.getWeakestDhStrength() != null) {
            if (report.getWeakestDhStrength() < 1000) {
                prettyAppendRed(builder, "DH Strength", "" + report.getWeakestDhStrength());
            } else if (report.getWeakestDhStrength() < 2000) {
                prettyAppendYellow(builder, "DH Strength", "" + report.getWeakestDhStrength());
            } else if (report.getWeakestDhStrength() < 4100) {
                prettyAppendGreen(builder, "DH Strength", "" + report.getWeakestDhStrength());
            } else {
                prettyAppendYellow(builder, "DH Strength", "" + report.getWeakestDhStrength());
            }
        }
    }

    private void appendScoringResults(StringBuilder builder) {
        prettyAppendHeading(builder, "Scoring results");

        SiteReportRater rater;
        try {
            rater = SiteReportRater.getSiteReportRater("en");
            ScoreReport scoreReport = rater.getScoreReport(report.getResultMap());
            prettyAppend(builder, "Score: " + scoreReport.getScore());
            if (!detail.isGreaterEqualTo(ScannerDetail.DETAILED)) {
                return;
            }
            prettyAppend(builder, "");
            scoreReport.getInfluencers().entrySet().forEach((entry) -> {
                PropertyResultRatingInfluencer influencer = entry.getValue();
                Recommendation recommendation = rater.getRecommendations().getRecommendation(entry.getKey());
                int scoreInluence = 0;
                StringBuilder additionalInfo = new StringBuilder();
                if (influencer.getReferencedProperty() != null) {
                    additionalInfo.append(" (Score: 0). -> See ").append(influencer.getReferencedProperty())
                            .append(" for more information");
                } else {
                    scoreInluence = influencer.getInfluence();
                    additionalInfo.append(" (Score: ").append((scoreInluence > 0 ? "+" : "")).append(scoreInluence);
                    if (influencer.hasScoreCap()) {
                        additionalInfo.append(", Score cap: ").append(influencer.getScoreCap());
                    }
                    additionalInfo.append(")");
                }
                String result = recommendation.getShortName() + ": " + influencer.getResult() + additionalInfo;
                if (scoreInluence > 0) {
                    prettyAppendGreen(builder, result);
//                } else if(scoreInluence == 0) {
//                    prettyAppend(builder, result);
                } else if (scoreInluence < -50) {
                    prettyAppendRed(builder, result);
                } else if (scoreInluence < 0) {
                    prettyAppendYellow(builder, result);
                }
            });
        } catch (JAXBException ex) {
            prettyAppendRed(builder, ex.getLocalizedMessage());
        }
    }

    private void appendRecommendations(StringBuilder builder) {
        prettyAppendHeading(builder, "Recommedations");

        SiteReportRater rater;
        try {
            rater = SiteReportRater.getSiteReportRater("en");
            ScoreReport scoreReport = rater.getScoreReport(report.getResultMap());
            scoreReport.getInfluencers().entrySet().forEach((entry) -> {
                PropertyResultRatingInfluencer influencer = entry.getValue();
                if (influencer.hasNegativeScore() || influencer.getReferencedProperty() != null) {
                    Recommendation recommendation = rater.getRecommendations().getRecommendation(entry.getKey());
                    PropertyResultRecommendation resultRecommendation = recommendation.getPropertyResultRecommendation(influencer.getResult());
                    if (detail.isGreaterEqualTo(ScannerDetail.DETAILED)) {
                        printFullRecommendation(builder, rater, recommendation, influencer, resultRecommendation);
                    } else {
                        printShortRecommendation(builder, resultRecommendation);
                    }
                }
            });
        } catch (JAXBException ex) {
            prettyAppendRed(builder, ex.getLocalizedMessage());
        }
    }

    private void printFullRecommendation(StringBuilder builder, SiteReportRater rater, Recommendation recommendation,
            PropertyResultRatingInfluencer influencer, PropertyResultRecommendation resultRecommendation) {
        prettyAppend(builder, "");
        prettyAppend(builder, recommendation.getShortName() + ": " + influencer.getResult());
        int scoreInluence = 0;
        String additionalInfo = "";
        if (influencer.getReferencedProperty() != null) {
            scoreInluence = rater.getRatingInfluencers().getPropertyRatingInfluencer(influencer.getReferencedProperty(),
                    influencer.getReferencedPropertyResult()).getInfluence();
            Recommendation r = rater.getRecommendations().getRecommendation(influencer.getReferencedProperty());
            additionalInfo = " -> This score comes from \"" + r.getShortName() + "\"";
        } else {
            scoreInluence = influencer.getInfluence();
        }
        prettyAppend(builder, "  Score: " + scoreInluence + additionalInfo);
        if (influencer.hasScoreCap()) {
            prettyAppend(builder, "  Score cap: " + influencer.getScoreCap());
        }
        prettyAppend(builder, "  Information: " + resultRecommendation.getShortDescription());
        prettyAppend(builder, "  Recommendation: " + resultRecommendation.getHandlingRecommendation());
    }

    private void printShortRecommendation(StringBuilder builder, PropertyResultRecommendation resultRecommendation) {
        prettyAppend(builder, resultRecommendation.getShortDescription() + ". " + resultRecommendation.getHandlingRecommendation());
    }

    private void prettyPrintCipherSuite(StringBuilder builder, CipherSuite suite) {
        CipherSuiteGrade grade = CiphersuiteRater.getGrade(suite);
        switch (grade) {
            case GOOD:
                prettyAppendGreen(builder, suite.name());
                break;
            case LOW:
                prettyAppendRed(builder, suite.name());
                break;
            case MEDIUM:
                prettyAppendYellow(builder, suite.name());
                break;
            case NONE:
                prettyAppend(builder, suite.name());
                break;
            default:
                prettyAppend(builder, suite.name());
        }
    }

    private StringBuilder appendCurves(StringBuilder builder) {
        if (report.getSupportedNamedGroups() != null) {
            prettyAppendHeading(builder, "Supported Named Groups");
            if (report.getSupportedNamedGroups().size() > 0) {
                for (NamedGroup group : report.getSupportedNamedGroups()) {
                    builder.append(group.name()).append("\n");
                }
            } else {
                builder.append("none\n");
            }
        }
        return builder;
    }

    private StringBuilder appendSignatureAndHashAlgorithms(StringBuilder builder) {
        if (report.getSupportedSignatureAndHashAlgorithms() != null) {
            prettyAppendHeading(builder, "Supported Signature and Hash Algorithms");
            if (report.getSupportedSignatureAndHashAlgorithms().size() > 0) {
                for (SignatureAndHashAlgorithm algorithm : report.getSupportedSignatureAndHashAlgorithms()) {
                    prettyAppend(builder, algorithm.toString());
                }
            } else {
                builder.append("none\n");
            }
        }
        return builder;
    }

    private StringBuilder appendCompressions(StringBuilder builder) {
        if (report.getSupportedCompressionMethods() != null) {
            prettyAppendHeading(builder, "Supported Compressions");
            for (CompressionMethod compression : report.getSupportedCompressionMethods()) {
                prettyAppend(builder, compression.name());
            }
        }
        return builder;
    }

    private String getBlackString(String value, String format) {
        return String.format(format, value == null ? "Unknown" : value);
    }

    private String getGreenString(String value, String format) {
        return (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + String.format(format, value == null ? "Unknown" : value) + AnsiColors.ANSI_RESET;
    }

    private String getYellowString(String value, String format) {
        return (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + String.format(format, value == null ? "Unknown" : value) + AnsiColors.ANSI_RESET;
    }

    private String getRedString(String value, String format) {
        return (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + String.format(format, value == null ? "Unknown" : value) + AnsiColors.ANSI_RESET;
    }

    private StringBuilder prettyAppend(StringBuilder builder, String value) {
        return builder.append(value == null ? "Unknown" : value).append("\n");
    }

    private StringBuilder prettyAppend(StringBuilder builder, String name, String value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : value).append("\n");
    }

    private StringBuilder prettyAppend(StringBuilder builder, String name, Long value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : value).append("\n");
    }

    private StringBuilder prettyAppend(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : value).append("\n");
    }

    private StringBuilder prettyAppend(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : value).append("\n");
    }

    private StringBuilder prettyAppendGreenOnSuccess(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendGreenOnSuccess(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendGreenOnFailure(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendGreenOnFailure(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendRedOnSuccess(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendRedOnSuccess(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendRedOnFailure(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendRedOnFailure(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellowOnFailure(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellowOnFailure(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? value : (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellowOnSuccess(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendYellowOnSuccess(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendGreenRed(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendGreenRed(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendRedGreen(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendRedGreen(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendGreenYellow(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendGreenYellow(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellowGreen(StringBuilder builder, String name, Boolean value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == Boolean.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellowGreen(StringBuilder builder, String name, TestResult value) {
        return builder.append(addIndentations(name)).append(": ").append(value == null ? "Unknown" : (value == TestResult.TRUE ? (report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET : (report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET)).append("\n");
    }

    private StringBuilder prettyAppendYellow(StringBuilder builder, String value) {
        return builder.append((report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendYellow(StringBuilder builder, String name, String value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_YELLOW : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendRed(StringBuilder builder, String value) {
        return builder.append((report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendRed(StringBuilder builder, String name, String value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_RED : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendGreen(StringBuilder builder, String value) {
        return builder.append((report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendGreen(StringBuilder builder, String name, String value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_GREEN : AnsiColors.ANSI_RESET) + value + AnsiColors.ANSI_RESET).append("\n");
    }

    private StringBuilder prettyAppendHeading(StringBuilder builder, String value) {
        depth = 0;
        return builder.append((report.isNoColor() == false ? AnsiColors.ANSI_BOLD + AnsiColors.ANSI_BLUE : AnsiColors.ANSI_RESET) + "\n------------------------------------------------------------\n" + value + "\n\n" + AnsiColors.ANSI_RESET);
    }

    private StringBuilder prettyAppendUnderlined(StringBuilder builder, String name, String value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_UNDERLINE + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendUnderlined(StringBuilder builder, String name, boolean value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_UNDERLINE + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendUnderlined(StringBuilder builder, String name, long value) {
        return builder.append(addIndentations(name)).append(": ").append((report.isNoColor() == false ? AnsiColors.ANSI_UNDERLINE + value + AnsiColors.ANSI_RESET : value)).append("\n");
    }

    private StringBuilder prettyAppendSubheading(StringBuilder builder, String name) {
        depth = 1;
        return builder.append("|_\n |" + (report.isNoColor() == false ? AnsiColors.ANSI_BOLD + AnsiColors.ANSI_PURPLE + AnsiColors.ANSI_UNDERLINE + name + "\n\n" + AnsiColors.ANSI_RESET : name + "\n\n"));
    }

    private StringBuilder prettyAppendSubSubheading(StringBuilder builder, String name) {
        depth = 2;
        return builder.append("|_\n |_\n  |" + (report.isNoColor() == false ? AnsiColors.ANSI_BOLD + AnsiColors.ANSI_PURPLE + AnsiColors.ANSI_UNDERLINE + name + "\n\n" + AnsiColors.ANSI_RESET : name + "\n\n"));
    }

    private StringBuilder prettyAppendSubSubSubheading(StringBuilder builder, String name) {
        depth = 3;
        return builder.append("|_\n |_\n  |_\n   |" + (report.isNoColor() == false ? AnsiColors.ANSI_BOLD + AnsiColors.ANSI_PURPLE + AnsiColors.ANSI_UNDERLINE + name + "\n\n" + AnsiColors.ANSI_RESET : name + "\n\n"));
    }

    private void prettyAppendDrown(StringBuilder builder, String testName, DrownVulnerabilityType drownVulnerable) {
        builder.append(addIndentations(testName)).append(": ");
        if (drownVulnerable == null) {
            prettyAppend(builder, "Unknown");
            return;
        }
        switch (drownVulnerable) {
            case FULL:
                prettyAppendRed(builder, "true - fully exploitable");
                break;
            case SSL2:
                prettyAppendRed(builder, "true - SSL 2 supported!");
                break;
            case NONE:
                prettyAppendGreen(builder, "false");
                break;
            case UNKNOWN:
                prettyAppend(builder, "Unknown");
                break;
        }
    }

    private void prettyAppendEarlyCcs(StringBuilder builder, String testName, EarlyCcsVulnerabilityType earlyCcsVulnerable) {
        builder.append(addIndentations(testName)).append(": ");
        if (earlyCcsVulnerable == null) {
            prettyAppend(builder, "Unknown");
            return;
        }
        switch (earlyCcsVulnerable) {
            case VULN_EXPLOITABLE:
                prettyAppendRed(builder, "true - exploitable");
                break;
            case VULN_NOT_EXPLOITABLE:
                prettyAppendRed(builder, "true - probably not exploitable");
                break;
            case NOT_VULNERABLE:
                prettyAppendGreen(builder, "false");
                break;
            case UNKNOWN:
                prettyAppend(builder, "Unknown");
                break;
        }
    }

    private StringBuilder prettyAppendCheckPattern(StringBuilder builder, String value, CheckPattern pattern) {
        if (pattern == null) {
            return builder.append(addIndentations(value)).append(": ").append("Unknown").append("\n");
        }
        builder = builder.append(addIndentations(value)).append(": ");
        switch (pattern.getType()) {
            case CORRECT:
                return prettyAppendGreen(builder, pattern.toString());
            case NONE:
            case PARTIAL:
                return prettyAppendRed(builder, pattern.toString());
            case UNKNOWN:
                return prettyAppend(builder, pattern.toString());
            default:
                throw new IllegalArgumentException("Unkown MacCheckPattern Type: " + pattern.getType());
        }
    }

    private String padToLength(String value, int length) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.append(" ");
        }
        return builder.toString();
    }

    private String addIndentations(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append(" ");
        }
        builder.append(value);
        if (value.length() + depth < 8) {
            builder.append("\t\t\t\t ");
        } else if (value.length() + depth < 16) {
            builder.append("\t\t\t ");
        } else if (value.length() + depth < 24) {
            builder.append("\t\t ");
        } else if (value.length() + depth < 32) {
            builder.append("\t ");
        } else {
            builder.append(" ");
        }
        return builder.toString();
    }

    private StringBuilder appendTls13Groups(StringBuilder builder) {
        if (report.getSupportedTls13Groups() != null) {
            prettyAppendHeading(builder, "TLS 1.3 Named Groups");
            if (report.getSupportedTls13Groups().size() > 0) {
                for (NamedGroup group : report.getSupportedTls13Groups()) {
                    builder.append(group.name()).append("\n");
                }
            } else {
                builder.append("none\n");
            }
        }
        return builder;
    }

    private void prettyAppendRandom(StringBuilder builder, String testName, RandomEvaluationResult randomEvaluationResult) {
        builder.append(addIndentations(testName)).append(": ");
        if (randomEvaluationResult == null) {
            prettyAppend(builder, "Unknown");
            return;
        }
        switch (randomEvaluationResult) {
            case DUPLICATES:
                prettyAppendRed(builder, "true - exploitable");
                break;
            case NOT_ANALYZED:
                prettyAppend(builder, "Not Analyzed");
                break;
            case NOT_RANDOM:
                prettyAppendRed(builder, "Does not seem to be Random");
                break;
            case UNIX_TIME:
                prettyAppend(builder, "Contains UnixTime");
                break;
            case NO_DUPLICATES:
                prettyAppendGreen(builder, "No Duplicates (wip)");
                break;
        }
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
