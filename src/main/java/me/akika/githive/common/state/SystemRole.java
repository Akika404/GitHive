package me.akika.githive.common.state;

import lombok.Getter;

@Getter
public enum SystemRole {

    SYSTEM_USER(0, "普通用户"),
    SYSTEM_ADMIN(1, "系统管理员");

    private final int code;
    private final String description;

    SystemRole(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
