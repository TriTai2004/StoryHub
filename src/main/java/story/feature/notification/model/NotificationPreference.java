package story.feature.notification.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.UpdateTimestamp;
import story.feature.user.model.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_preferences", indexes = {
    @Index(name = "idx_notification_preferences_1", columnList = "user_id")
})
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private User user;

    @Column(name = "post_likes_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean postLikesEnabled = true;

    @Column(name = "post_comments_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean postCommentsEnabled = true;

    @Column(name = "comment_replies_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean commentRepliesEnabled = true;

    @Column(name = "report_updates_enabled", nullable = false)
    @ColumnDefault("true")
    private Boolean reportUpdatesEnabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    @ColumnDefault("now()")
    private Instant updatedAt;
}
