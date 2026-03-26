package com.studytool.shared.a2ui;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2UIMessage {

    private String text;
    private List<A2UIComponent> components;

    public static A2UIMessage textOnly(String text) {
        return A2UIMessage.builder().text(text).build();
    }

    public static A2UIMessage withComponents(String text, List<A2UIComponent> components) {
        return A2UIMessage.builder().text(text).components(components).build();
    }
}
