package com.assignment.whiteboard.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private String error;
    private String message;
}