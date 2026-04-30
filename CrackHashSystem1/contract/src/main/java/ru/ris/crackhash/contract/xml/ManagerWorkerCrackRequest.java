package ru.ris.crackhash.contract.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "crackHashManagerRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "requestId",
        "hash",
        "alphabet",
        "maxLength",
        "partNumber",
        "partCount"
})
public class ManagerWorkerCrackRequest {

    @XmlElement(required = true)
    private String requestId;

    @XmlElement(required = true)
    private String hash;

    @XmlElement(required = true)
    private String alphabet;

    private int maxLength;
    private int partNumber;
    private int partCount;

    public ManagerWorkerCrackRequest() {
    }

    public ManagerWorkerCrackRequest(String requestId, String hash, String alphabet, int maxLength, int partNumber, int partCount) {
        this.requestId = requestId;
        this.hash = hash;
        this.alphabet = alphabet;
        this.maxLength = maxLength;
        this.partNumber = partNumber;
        this.partCount = partCount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getAlphabet() {
        return alphabet;
    }

    public void setAlphabet(String alphabet) {
        this.alphabet = alphabet;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public int getPartCount() {
        return partCount;
    }

    public void setPartCount(int partCount) {
        this.partCount = partCount;
    }
}
