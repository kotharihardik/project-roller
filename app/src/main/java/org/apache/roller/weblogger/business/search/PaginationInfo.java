package org.apache.roller.weblogger.business.search;

public class PaginationInfo {
    
    private final int limit;
    private final int offset;
    private final int totalResults;
    
    public PaginationInfo(int limit, int offset, int totalResults) {
        this.limit = limit;
        this.offset = offset;
        this.totalResults = totalResults;
    }
    
    public int getLimit() {
        return limit;
    }
    
    public int getOffset() {
        return offset;
    }
    
    public int getTotalResults() {
        return totalResults;
    }
    
    public boolean hasMoreResults() {
        return totalResults > (offset + limit);
    }
    
    public int getPageNumber() {
        return offset / limit;
    }
    
    public boolean hasNextPage() {
        return hasMoreResults();
    }
    
    public boolean hasPreviousPage() {
        return offset > 0;
    }
}
