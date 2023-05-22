package com.assignment.whiteboard.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApiRequest <T> {
    private String sourceUsername;
    private T data;
    private String message;
}