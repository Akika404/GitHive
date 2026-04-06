package me.akika.githive.common.state;

import lombok.Getter;

@Getter
public enum NamespaceType {

    USER(0, "用户"),
    ORG(1, "组织");

    private final int code;
    private final String description;

    NamespaceType(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
