package com.oac.enforcement;

public class NoOpDecisionClient implements DecisionClient {

    @Override
    public boolean checkPermission(String subjectId, String action, String resourceId) {
        return false;
    }
}
