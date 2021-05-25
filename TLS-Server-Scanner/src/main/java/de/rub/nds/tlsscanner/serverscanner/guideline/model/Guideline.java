/**
 * TLS-Server-Scanner - A TLS configuration and analysis tool based on TLS-Attacker
 *
 * Copyright 2017-2021 Ruhr University Bochum, Paderborn University, Hackmanit GmbH
 *
 * Licensed under Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 */

package de.rub.nds.tlsscanner.serverscanner.guideline.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@XmlRootElement(name = "guideline")
public class Guideline implements Serializable {

    private String name;
    private String link;
    private List<GuidelineCheck> checks;
    private List<GuidelineCipherSuites> cipherSuites;

    public Guideline() {
    }

    public String getName() {
        return name;
    }

    @XmlElement(name = "name")
    public void setName(String name) {
        this.name = name;
    }

    public String getLink() {
        return link;
    }

    @XmlElement(name = "link")
    public void setLink(String link) {
        this.link = link;
    }

    public List<GuidelineCheck> getChecks() {
        return checks;
    }

    @XmlElement(name = "check")
    @XmlElementWrapper(name = "checks")
    public void setChecks(List<GuidelineCheck> checks) {
        this.checks = checks;
    }

    public List<GuidelineCipherSuites> getCipherSuites() {
        return cipherSuites;
    }

    @XmlElement(name = "cipherSuite")
    @XmlElementWrapper(name = "cipherSuites")
    public void setCipherSuites(List<GuidelineCipherSuites> cipherSuites) {
        this.cipherSuites = cipherSuites;
    }

    @Override
    public String toString() {
        return "Guideline{" + "checks=" + checks + ", cipherSuites=" + cipherSuites + '}';
    }
}
