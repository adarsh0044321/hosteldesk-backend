package com.hosteldesk.backend.dto;

public class VerifyResolutionRequest {
    private String satisfactionNote;

    public VerifyResolutionRequest() {}

    public VerifyResolutionRequest(String satisfactionNote) {
        this.satisfactionNote = satisfactionNote;
    }

    public String getSatisfactionNote() { return satisfactionNote; }
    public void setSatisfactionNote(String satisfactionNote) { this.satisfactionNote = satisfactionNote; }
}
