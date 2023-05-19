package com.assignment.whiteboard.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShapeDTO {
    private String type;
    private int x, y, width, height;
    private int r, g, b;
}
