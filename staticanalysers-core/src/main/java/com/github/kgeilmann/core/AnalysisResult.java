package com.github.kgeilmann.core;

public class AnalysisResult {

    private final String expression;
    private String filePath;
    private String location;
    private String message;

    public AnalysisResult(String location, String expression, String message) {
        this.location = location;
        this.expression = expression;
        this.message = message;
    }

    public String getExpression() {
        return expression;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

}
