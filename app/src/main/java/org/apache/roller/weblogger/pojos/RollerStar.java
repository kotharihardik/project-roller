package org.apache.roller.weblogger.pojos;

import java.io.Serializable;
import java.sql.Timestamp;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.roller.util.UUIDGenerator;

public class RollerStar implements Serializable {

    public enum TargetType { WEBLOG, ENTRY }

    private String id = UUIDGenerator.generateUUID();
    private String starredByUserId;
    private TargetType targetEntityType;
    private String targetEntityId;
    private Timestamp starredAt;

    public RollerStar() {}

    public RollerStar(String starredByUserId, TargetType targetEntityType, String targetEntityId, Timestamp starredAt) {
        this.starredByUserId = starredByUserId;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.starredAt = starredAt;
    }

    public String getId() {
        return id; 
    }
    
    public void setId(String id) {
        this.id = id;
    }

    public String getStarredByUserId() {
        return starredByUserId;
    }
    
    public void setStarredByUserId(String starredByUserId) {
        this.starredByUserId = starredByUserId;
    }

    public TargetType getTargetEntityType() {
        return targetEntityType;
    }
    
    public void setTargetEntityType(TargetType targetEntityType) {
        this.targetEntityType = targetEntityType;
    }

    public String getTargetEntityId() {
        return targetEntityId;
    }

    public void setTargetEntityId(String targetEntityId) {
        this.targetEntityId = targetEntityId;
    }

    public Timestamp getStarredAt() {
        return starredAt;
    }
    
    public void setStarredAt(Timestamp starredAt) {
        this.starredAt = starredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RollerStar)) return false;
        RollerStar o = (RollerStar) other;
        return new EqualsBuilder()
                .append(starredByUserId, o.starredByUserId)
                .append(targetEntityType, o.targetEntityType)
                .append(targetEntityId, o.targetEntityId)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(starredByUserId)
                .append(targetEntityType)
                .append(targetEntityId)
                .toHashCode();
    }
}