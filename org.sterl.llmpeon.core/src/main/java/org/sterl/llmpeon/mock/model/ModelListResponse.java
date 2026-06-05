package org.sterl.llmpeon.mock.model;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import lombok.Data;
import lombok.Getter;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Getter
public class ModelListResponse {

    private String object = "list";
    private List<ModelInfo> data = Collections.emptyList();

    public ModelListResponse(List<String> modelIds) {
        this.data = modelIds.stream()
                .map(id -> new ModelInfo(id))
                .toList();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Data
    public static class ModelInfo {
        private String id;
        private String object = "model";
        private long created = System.currentTimeMillis() / 1000;
        private String owned_by = "mock";

        public ModelInfo(String id) { this.id = id; }
    }
}
