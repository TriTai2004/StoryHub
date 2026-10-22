package story.feature.report.model;

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
import story.feature.user.model.User;

/**
 * Hibernate prevents updates; append-only access and deletion retention still require application/database permissions.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "report_actions", indexes = {
    @Index(name = "idx_report_actions_1", columnList = "report_id"),
    @Index(name = "idx_report_actions_2", columnList = "admin_id")
})
@Immutable
public class ReportAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private User admin;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private ActionType actionType;

    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> afterState;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz", updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    public enum ActionType {
        ASSIGN, START_REVIEW, REJECT, RESOLVE, HIDE_CONTENT, RESTORE_CONTENT, WARN_USER, SUSPEND_USER, BAN_USER, NOTE
    }
}
