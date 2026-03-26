package com.studytool.shared.a2ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIComponent {

    private String type;
    private String id;
    private Map<String, Object> props;
    private java.util.List<A2UIComponent> children;
}
