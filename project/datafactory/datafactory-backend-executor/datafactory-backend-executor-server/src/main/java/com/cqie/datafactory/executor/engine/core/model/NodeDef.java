package com.cqie.datafactory.executor.engine.core.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class NodeDef {
    private String id;
    private String type;
    private String name;
    private String label;
    private String componentCode;
    private Long componentId;
    private Double positionX;
    private Double positionY;
    private JsonNode fieldValues;
    private List<IoParamDef> inputParams = new ArrayList<>();
    private List<IoParamDef> outputParams = new ArrayList<>();
    private JsonNode raw;

    public NodeDef() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public Double getPositionX() { return positionX; }
    public void setPositionX(Double positionX) { this.positionX = positionX; }

    public Double getPositionY() { return positionY; }
    public void setPositionY(Double positionY) { this.positionY = positionY; }

    public JsonNode getFieldValues() { return fieldValues; }
    public void setFieldValues(JsonNode fieldValues) { this.fieldValues = fieldValues; }

    public List<IoParamDef> getInputParams() { return inputParams; }
    public void setInputParams(List<IoParamDef> inputParams) { this.inputParams = inputParams; }

    public List<IoParamDef> getOutputParams() { return outputParams; }
    public void setOutputParams(List<IoParamDef> outputParams) { this.outputParams = outputParams; }

    public JsonNode getRaw() { return raw; }
    public void setRaw(JsonNode raw) { this.raw = raw; }

    public String getDisplayName() {
        return (name != null && !name.isBlank()) ? name
                : (label != null && !label.isBlank()) ? label : id;
    }

    public static class IoParamDef {
        private String paramCode;
        private String paramName;
        private String paramType;
        private String dataType;
        private String sourceType;
        private JsonNode sourceValue;
        private String defaultValue;
        private int requiredFlag;
        private String paramSpace;

        public IoParamDef() {}

        public String getParamCode() { return paramCode; }
        public void setParamCode(String paramCode) { this.paramCode = paramCode; }
        public String getParamName() { return paramName; }
        public void setParamName(String paramName) { this.paramName = paramName; }
        public String getParamType() { return paramType; }
        public void setParamType(String paramType) { this.paramType = paramType; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public String getSourceType() { return sourceType; }
        public void setSourceType(String sourceType) { this.sourceType = sourceType; }
        public JsonNode getSourceValue() { return sourceValue; }
        public void setSourceValue(JsonNode sourceValue) { this.sourceValue = sourceValue; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public int getRequiredFlag() { return requiredFlag; }
        public void setRequiredFlag(int requiredFlag) { this.requiredFlag = requiredFlag; }
        public String getParamSpace() { return paramSpace; }
        public void setParamSpace(String paramSpace) { this.paramSpace = paramSpace; }
    }
}
