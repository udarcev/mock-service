package ru.demo.servicemock;

import org.w3c.dom.Document;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;


class ServiceMock {
    private Document request;
    private Document response;

    private Set<String> variables = Collections.EMPTY_SET;

    private String callbackEndpoint;
    private File callbackBodyFile;
    private Long callbackDelayMillis;

    private int responseStatus = HttpServletResponse.SC_OK;

    public ServiceMock(Document request, Document response) {
        this.request = request;
        this.response = response;
    }

    public Document getRequest() {
        return request;
    }

    public void setRequest(Document request) {
        this.request = request;
    }

    public Document getResponse() {
        return response;
    }

    public void setResponse(Document response) {
        this.response = response;
    }

    public Set<String> getVariables() {
        return variables;
    }

    public void setVariables(Set<String> variables) {
        this.variables = variables;
    }

    public String getCallbackEndpoint() {
        return callbackEndpoint;
    }

    public void setCallbackEndpoint(String callbackEndpoint) {
        this.callbackEndpoint = callbackEndpoint;
    }

    public File getCallbackBodyFile() {
        return callbackBodyFile;
    }

    public void setCallbackBodyFile(File callbackBodyFile) {
        this.callbackBodyFile = callbackBodyFile;
    }

    public Long getCallbackDelayMillis() {
        return callbackDelayMillis;
    }

    public void setCallbackDelayMillis(Long callbackDelayMillis) {
        this.callbackDelayMillis = callbackDelayMillis;
    }

    public boolean hasCallback() {
        return callbackBodyFile != null && callbackEndpoint != null;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }
}

class CustomMock {
    private String field;
    private String value;
    private String mock;
    private Document document;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getMock() {
        return mock;
    }

    public void setMock(String mock) {
        this.mock = mock;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }
}

class IncomingRequest {
    private long date;
    private Document document;

    public IncomingRequest(Document document, long date) {
        this.date = date;
        this.document = document;
    }

    public long getDate() {
        return date;
    }

    public Document getDocument() {
        return document;
    }

    public String getStringDocument() {
        try {
            return MockHttpServlet.toString(document);
        } catch (ServletException e) {
            return "";
        }
    }
}

class RequestParameters {
    public long dateFrom;
    public long dateTo;
    public HashMap<String, String> parameters;

    public RequestParameters(HashMap<String, String> parameters) {
        this.parameters = parameters;
        dateFrom = -1;
        dateTo = -1;
    }

    public RequestParameters(long dateFrom, long dateTo) {
        this.parameters = null;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    public RequestParameters(HashMap<String, String> parameters, long dateFrom, long dateTo) {
        this.parameters = parameters;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
    }

    @Override
    public String toString() {
        return "RequestParameters{" +
                "dateFrom=" + dateFrom +
                ", dateTo=" + dateTo +
                ", parameters=" + parameters +
                '}';
    }
}

