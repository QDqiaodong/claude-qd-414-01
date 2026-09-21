package com.coldstore.freezer.dto;

/** 异常巡检闭环请求：闭环说明选填 */
public class InspectionCloseReq {
    private String closeNote;

    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }
}
