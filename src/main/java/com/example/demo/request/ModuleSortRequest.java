package com.example.demo.request;

import lombok.Data;
import java.util.List;

@Data
public class ModuleSortRequest {
    private List<SortItem> moduleOrders;

    @Data
    public static class SortItem {
        private Long id;
        private Integer sortOrder;
    }
}
