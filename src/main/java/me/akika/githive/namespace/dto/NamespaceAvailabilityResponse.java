package me.akika.githive.namespace.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceAvailabilityResponse {

    private String path;

    private boolean available;

    private String message;
}
