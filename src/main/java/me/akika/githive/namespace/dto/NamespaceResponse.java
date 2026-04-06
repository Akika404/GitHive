package me.akika.githive.namespace.dto;

import lombok.*;
import me.akika.githive.common.state.NamespaceType;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceResponse {

    private Long id;

    private String path;

    private String displayPath;

    private NamespaceType ownerType;

    private Long ownerId;

    private LocalDateTime createdAt;
}
