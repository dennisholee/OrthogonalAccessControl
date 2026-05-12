package com.oac.enforcement;

public interface DecisionClient {

    boolean checkPermission(String subjectId, String action, String resourceId);
}
