package org.sterl.llmpeon.mock.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ModelListResponse {

    private String object = "list";
    private List<ModelInfo> data;

    public ModelListResponse(List<String> modelIds) {
        this.data = modelIds.stream()
                .map(id -> new ModelInfo(id))
                .toList();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class ModelInfo {
        private String id;
        private String object = "model";
        private long created = System.currentTimeMillis() / 1000;
        private String owned_by = "mock";

        public ModelInfo(String id) { this.id = id; }
    }
}
