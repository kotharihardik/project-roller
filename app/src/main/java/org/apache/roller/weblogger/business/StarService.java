package org.apache.roller.weblogger.business;

import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.pojos.RollerStar;
import org.apache.roller.weblogger.pojos.StatCount;

public interface StarService {

    void star(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException;

    void unstar(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException;

    boolean isStarred(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException;

    long getStarCount(RollerStar.TargetType type, String targetId) throws WebloggerException;

    List<StarredWeblogView> getStarredWeblogsSortedByLatestPost(String userId, int offset, int length) throws WebloggerException;

    List<StatCount> getTopStarredEntries(int offset, int length) throws WebloggerException;

    List<StatCount> getTopStarredWeblogs(int offset, int length) throws WebloggerException;
}