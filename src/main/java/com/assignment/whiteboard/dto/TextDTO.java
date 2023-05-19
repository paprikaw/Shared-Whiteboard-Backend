package com.assignment.whiteboard.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TextDTO {
    private int x;
    private int y;
    private String text;
    private int r, g, b;
    private int font_size;
}
