package story.feature.moderation.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import story.feature.report.model.Report;
import story.feature.user.model.User;

/**
 * Hibernate prevents updates; append-only access and deletion retention still require application/database permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "moderation_audit_logs", indexes = {
    @Index(name = "idx_moderation_audit_logs_1", columnList = "actor_id"),
    @Index(name = "idx_moderation_audit_logs_2", columnList = "related_report_id")
})
@Immutable
public class ModerationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    @Column(name = "reason", nullable = true, columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "related_report_id", nullable = true)
    private Report relatedReport;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum TargetType {
        POST, COMMENT, PLACE, USER, REPORT
    }

    public enum ActionType {
        HIDE, RESTORE, WARN, SUSPEND, BAN, MERGE_PLACE, DELETE, UPDATE_PLACE, OTHER
    }
}
