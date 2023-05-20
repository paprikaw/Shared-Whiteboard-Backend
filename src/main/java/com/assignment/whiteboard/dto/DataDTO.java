package com.assignment.whiteboard.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DataDTO {
    private List<ShapeDTO> shapeList;
    private List<TextDTO> textList;
    private List<LineDTO> lineList;
}