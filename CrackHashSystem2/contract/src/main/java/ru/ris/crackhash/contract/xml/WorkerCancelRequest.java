package ru.ris.crackhash.contract.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlRootElement(name = "crackHashCancelRequest")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {
        "requestId",
        "cancelAll"
})
public class WorkerCancelRequest {

    private String requestId;
    private boolean cancelAll;

    public WorkerCancelRequest() {
    }

    public WorkerCancelRequest(String requestId, boolean cancelAll) {
        this.requestId = requestId;
        this.cancelAll = cancelAll;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isCancelAll() {
        return cancelAll;
    }

    public void setCancelAll(boolean cancelAll) {
        this.cancelAll = cancelAll;
    }
}
