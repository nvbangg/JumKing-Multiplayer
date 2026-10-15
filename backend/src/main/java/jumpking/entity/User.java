package jumpking.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "type", length = 16)
    private String type;
    @Column(name = "username", length = 32)
    private String username;
    @Column(name = "email", length = 254)
    private String email;
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "guest_token_hash", length = 64)
    private String guestTokenHash;
    @Column(name = "merged_into_user_id")
    private Long mergedIntoUserId;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", columnDefinition = "datetime(3)")
    private Instant createdAt;

    protected User() {}
    public static User guest(String hash, Instant created) {
        var user = new User(); user.type = "GUEST"; user.guestTokenHash = hash; user.createdAt = created; return user;
    }
    public static User account(String username, String email, String passwordHash, Instant created) {
        var user = new User(); user.type = "ACCOUNT"; user.username = username; user.email = email;
        user.passwordHash = passwordHash; user.createdAt = created; return user;
    }
    public Long getId() { return id; }
    public String getType() { return type; }
    public void setType(String value) { type = value; }
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public String getGuestTokenHash() { return guestTokenHash; }
    public void setGuestTokenHash(String value) { guestTokenHash = value; }
    public Long getMergedIntoUserId() { return mergedIntoUserId; }
    public void setMergedIntoUserId(Long value) { mergedIntoUserId = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}
