package me.akika.githive.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@TableName("user_refresh_token")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String tokenHash;
    private String userAgent;
    private String createdIp;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
