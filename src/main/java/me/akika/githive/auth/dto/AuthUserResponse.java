package me.akika.githive.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {

    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String systemRole;
    private Boolean emailVerified;
}
