package me.akika.githive.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.akika.githive.common.state.SystemRole;

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
    private SystemRole systemRole;
    private Boolean emailVerified;
}
