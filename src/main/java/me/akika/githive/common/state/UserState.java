package me.akika.githive.common.state;

import lombok.Getter;

@Getter
public enum UserState {

    ACTIVE(0, "活跃"),
    INACTIVE(1, "不活跃"),
    BANNED(2, "封禁");

    private final int code;
    private final String description;

    UserState(int code, String description) {
        this.code = code;
        this.description = description;
    }

}
