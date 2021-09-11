package ru.demo.servicemock;

public class ConfigureRequest {

    private ActionType actionType;
    private String checkMockField;
    private String checkMockValue;
    private RequestParameters requestParameters;
    private CustomMock[] customMocks;

    public ActionType getActionType() {
        return actionType;
    }

    public String getCheckMockField() {
        return checkMockField;
    }

    public String getCheckMockValue() {
        return checkMockValue;
    }

    public RequestParameters getRequestParameters() {
        return requestParameters;
    }

    public CustomMock[] getCustomMocks() {
        return customMocks;
    }
}
