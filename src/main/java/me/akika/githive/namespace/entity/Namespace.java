package me.akika.githive.namespace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import me.akika.githive.common.state.NamespaceType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("namespace")
public class Namespace {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * URL 路由路径，统一小写存储，全局唯一
     */
    private String path;

    /**
     * 展示用路径，保留原始大小写
     */
    private String displayPath;

    /**
     * 所有者类型：USER / ORG
     */
    private NamespaceType ownerType;

    /**
     * 所有者 ID（对应 app_user.id 或 org.id）
     */
    private Long ownerId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
