package com.blade.catalog.dto;

import java.util.List;

/**
 * Available filter options for the catalog page.
 */
public class CatalogFiltersVO {

    private List<FilterOption> categories;
    private List<FilterOption> colors;
    private List<FilterOption> sizes;
    private List<FilterOption> stockModes;

    public static class FilterOption {
        private Long id;
        private String name;
        private String code;  // optional extra identifier

        public FilterOption() {}
        public FilterOption(Long id, String name) {
            this.id = id;
            this.name = name;
        }
        public FilterOption(Long id, String name, String code) {
            this.id = id;
            this.name = name;
            this.code = code;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public List<FilterOption> getCategories() { return categories; }
    public void setCategories(List<FilterOption> categories) { this.categories = categories; }

    public List<FilterOption> getColors() { return colors; }
    public void setColors(List<FilterOption> colors) { this.colors = colors; }

    public List<FilterOption> getSizes() { return sizes; }
    public void setSizes(List<FilterOption> sizes) { this.sizes = sizes; }

    public List<FilterOption> getStockModes() { return stockModes; }
    public void setStockModes(List<FilterOption> stockModes) { this.stockModes = stockModes; }
}
