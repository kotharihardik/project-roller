package org.apache.roller.weblogger.business.jpa;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.StarService;
import org.apache.roller.weblogger.business.StarredWeblogView;
import org.apache.roller.weblogger.pojos.RollerStar;
import org.apache.roller.weblogger.pojos.StatCount;
import org.apache.roller.weblogger.pojos.Weblog;
import org.apache.roller.weblogger.pojos.WeblogEntry;

import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

@com.google.inject.Singleton
public class JPAStarServiceImpl implements StarService {

    private final JPAPersistenceStrategy strategy;

    @com.google.inject.Inject
    public JPAStarServiceImpl(JPAPersistenceStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void star(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException {
        validateTarget(type, targetId);

        if (isStarred(userId, type, targetId)) {
            return;
        }

        RollerStar s = new RollerStar(userId, type, targetId, new Timestamp(new Date().getTime()));
        strategy.store(s);
    }

    @Override
    public void unstar(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException {
        TypedQuery<RollerStar> q = strategy.getNamedQuery("RollerStar.getByUserAndTarget", RollerStar.class);
        q.setParameter(1, userId);
        q.setParameter(2, type);
        q.setParameter(3, targetId);

        List<RollerStar> stars = q.getResultList();
        for (RollerStar s : stars) {
            strategy.remove(s);
        }
    }

    @Override
    public boolean isStarred(String userId, RollerStar.TargetType type, String targetId) throws WebloggerException {
        TypedQuery<RollerStar> q = strategy.getNamedQuery("RollerStar.getByUserAndTarget", RollerStar.class);
        q.setParameter(1, userId);
        q.setParameter(2, type);
        q.setParameter(3, targetId);
        return !q.getResultList().isEmpty();
    }

    @Override
    public long getStarCount(RollerStar.TargetType type, String targetId) throws WebloggerException {
        String jpql =
                "SELECT COUNT(DISTINCT s.starredByUserId) FROM RollerStar s " +
                "WHERE s.targetEntityType = :ttype AND s.targetEntityId = :tid";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter("ttype", type);
        q.setParameter("tid", targetId);

        Number count = (Number) q.getSingleResult();
        return count != null ? count.longValue() : 0L;
    }

    @Override
    public List<StarredWeblogView> getStarredWeblogsSortedByLatestPost(
            String userId, int offset, int length) throws WebloggerException {
        String jpql =
            "SELECT w, (SELECT MAX(e.updateTime) FROM WeblogEntry e WHERE e.website = w AND e.status = :published) " +
            "FROM RollerStar s, Weblog w " +
            "WHERE s.starredByUserId = :uid " +
            "  AND s.targetEntityType = :ttype " +
            "  AND s.targetEntityId = w.id " +
            "ORDER BY " +
            "  CASE WHEN (SELECT MAX(e2.updateTime) FROM WeblogEntry e2 WHERE e2.website = w AND e2.status = :published) IS NULL THEN 1 ELSE 0 END, " +
            "  (SELECT MAX(e3.updateTime) FROM WeblogEntry e3 WHERE e3.website = w AND e3.status = :published) DESC";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter("uid", userId);
        q.setParameter("ttype", RollerStar.TargetType.WEBLOG);
        q.setParameter("published", WeblogEntry.PubStatus.PUBLISHED);

        if (offset >= 0) q.setFirstResult(offset);
        if (length > 0) q.setMaxResults(length);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<StarredWeblogView> result = new ArrayList<>();
        for (Object[] r : rows) {
            Date lastUpdate = (Date) r[1];
            Timestamp ts = lastUpdate != null ? new Timestamp(lastUpdate.getTime()) : null;
            result.add(new StarredWeblogView((Weblog) r[0], ts));
        }
        return result;
    }

    @Override
    public List<StatCount> getTopStarredEntries(int offset, int length) throws WebloggerException {
        String jpql =
                "SELECT e.id, e.anchor, e.title, e.website.handle, COUNT(DISTINCT s.starredByUserId) " +
                "FROM RollerStar s, WeblogEntry e " +
                "WHERE s.targetEntityType = :ttype " +
                "  AND s.targetEntityId = e.id " +
                "  AND e.status = :published " +
                "GROUP BY e.id, e.anchor, e.title, e.website.handle " +
                "ORDER BY COUNT(DISTINCT s.starredByUserId) DESC, MAX(s.starredAt) DESC";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter("ttype", RollerStar.TargetType.ENTRY);
        q.setParameter("published", WeblogEntry.PubStatus.PUBLISHED);

        if (offset >= 0) {
            q.setFirstResult(offset);
        }
        if (length > 0) {
            q.setMaxResults(length);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<StatCount> result = new ArrayList<>();
        for (Object[] row : rows) {
            StatCount stat = new StatCount(
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    "statCount.entryStars",
                    ((Number) row[4]).longValue());
            stat.setWeblogHandle((String) row[3]);
            result.add(stat);
        }

        return result;
    }

    @Override
    public List<StatCount> getTopStarredWeblogs(int offset, int length) throws WebloggerException {
        String jpql =
                "SELECT w.id, w.handle, w.name, COUNT(DISTINCT s.starredByUserId) " +
                "FROM RollerStar s, Weblog w " +
                "WHERE s.targetEntityType = :ttype " +
                "  AND s.targetEntityId = w.id " +
                "GROUP BY w.id, w.handle, w.name " +
                "ORDER BY COUNT(DISTINCT s.starredByUserId) DESC, MAX(s.starredAt) DESC";

        Query q = strategy.getDynamicQuery(jpql);
        q.setParameter("ttype", RollerStar.TargetType.WEBLOG);

        if (offset >= 0) {
            q.setFirstResult(offset);
        }
        if (length > 0) {
            q.setMaxResults(length);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();

        List<StatCount> result = new ArrayList<>();
        for (Object[] row : rows) {
            StatCount stat = new StatCount(
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    "statCount.weblogStars",
                    ((Number) row[3]).longValue());
            stat.setWeblogHandle((String) row[1]);
            result.add(stat);
        }

        return result;
    }

    private void validateTarget(RollerStar.TargetType type, String targetId) throws WebloggerException {
        if (type == null || targetId == null || targetId.isBlank()) {
            throw new WebloggerException("Invalid star target");
        }
    }
}
