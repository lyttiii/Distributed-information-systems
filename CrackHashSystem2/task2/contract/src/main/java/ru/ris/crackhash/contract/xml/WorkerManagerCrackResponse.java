package ru.ris.crackhash.contract.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "crackHashWorkerResponse")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "requestId",
        "partNumber",
        "answers",
        "failed",
        "errorMessage"
})
public class WorkerManagerCrackResponse {

    @XmlElement(required = true)
    private String requestId;

    private int partNumber;

    @XmlElementWrapper(name = "answers")
    @XmlElement(name = "word")
    private List<String> answers = new ArrayList<>();

    private boolean failed;
    private String errorMessage;

    public WorkerManagerCrackResponse() {
    }

    public WorkerManagerCrackResponse(String requestId, int partNumber, List<String> answers) {
        this.requestId = requestId;
        this.partNumber = partNumber;
        this.answers = answers;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void setAnswers(List<String> answers) {
        this.answers = answers;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
