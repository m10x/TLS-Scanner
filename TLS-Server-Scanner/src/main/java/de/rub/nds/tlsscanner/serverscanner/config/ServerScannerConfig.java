/*
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2023 Ruhr University Bochum, Paderborn University, and Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */
package de.rub.nds.tlsscanner.serverscanner.config;

import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import de.rub.nds.tlsattacker.core.config.delegate.ClientAuthenticationDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.ClientDelegate;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsscanner.core.config.TlsScannerConfig;
import de.rub.nds.tlsscanner.serverscanner.constants.ApplicationProtocol;

public class ServerScannerConfig extends TlsScannerConfig {

    @ParametersDelegate
    private ClientDelegate clientDelegate;

    @ParametersDelegate
    private ClientAuthenticationDelegate clientAuthenticationDelegate;

    @Parameter(names = "-applicationProtocol", required = false, description = "Which application data protocol the server is running.")
    private ApplicationProtocol applicationProtocol = ApplicationProtocol.HTTP;

    @Parameter(names = "-additionalRandomCollection", required = false, description = "Number of connections that should be additionally performed to collect more randomness data to get more accurate analysis")
    private int additionalRandomnessHandshakes = 0;

    @Parameter(names = "-ca", required = false, variableArity = true, description = "Add one or more custom CA's by separating them with a comma to verify the corresponding chain of certificates.")
    private List<String> customCAPathList = null;

    @Parameter(names = "-configSearchCooldown", required = false, description = "Pause between config tests to ensure the server finished processing the previously rejected messages")
    private boolean configSearchCooldown = false;

    public ServerScannerConfig(GeneralDelegate delegate) {
        super(delegate);

        this.clientDelegate = new ClientDelegate();
        this.clientAuthenticationDelegate = new ClientAuthenticationDelegate();

        addDelegate(clientDelegate);
        addDelegate(clientAuthenticationDelegate);
    }

    public ServerScannerConfig(GeneralDelegate delegate, ClientDelegate clientDelegate) {
        super(delegate);

        this.clientDelegate = clientDelegate;
        this.clientAuthenticationDelegate = new ClientAuthenticationDelegate();

        addDelegate(clientDelegate);
        addDelegate(clientAuthenticationDelegate);
    }

    public ApplicationProtocol getApplicationProtocol() {
        return applicationProtocol;
    }

    public void setApplicationProtocol(ApplicationProtocol applicationProtocol) {
        this.applicationProtocol = applicationProtocol;
    }

    public ClientDelegate getClientDelegate() {
        return clientDelegate;
    }

    public ClientAuthenticationDelegate getClientAuthenticationDelegate() {
        return clientAuthenticationDelegate;
    }

    public int getAdditionalRandomnessHandshakes() {
        return additionalRandomnessHandshakes;
    }

    public void setAdditionalRandomnessHandshakes(int additionalRandomnessHandshakes) {
        this.additionalRandomnessHandshakes = additionalRandomnessHandshakes;
    }

    public List<String> getCustomCAPathList() {
        return customCAPathList;
    }

    public void setCustomCAPathList(List<String> customCAPathList) {
        this.customCAPathList = customCAPathList;
    }

    public boolean isConfigSearchCooldown() {
        return configSearchCooldown;
    }

    public void setConfigSearchCooldown(boolean configSearchCooldown) {
        this.configSearchCooldown = configSearchCooldown;
    }
}
