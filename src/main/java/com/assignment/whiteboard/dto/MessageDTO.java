package com.assignment.whiteboard.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageDTO {
    private String type; // 用于区分内部DTO的类型，如 "Text", "Line", "Rectangle" 等
    private Object payload; // 这是内部DTO的实例
}