package org.apache.roller.weblogger.business.breakdown;

import java.util.Collections;
import java.util.List;

public class Theme {

    private String name;
    private List<String> representativeComments;

    public Theme() {
        this.name                   = "";
        this.representativeComments = Collections.emptyList();
    }

    /**
     * @param name                   short theme label; must not be {@code null}.
     * @param representativeComments representative comment excerpts; must not be {@code null}.
     */
    public Theme(String name, List<String> representativeComments) {
        this.name                   = name;
        this.representativeComments = Collections.unmodifiableList(representativeComments);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getRepresentativeComments() {
        return representativeComments;
    }

    public void setRepresentativeComments(List<String> representativeComments) {
        this.representativeComments = representativeComments;
    }
}
