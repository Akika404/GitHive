package me.akika.githive.namespace.service;

import me.akika.githive.auth.entity.AppUser;
import me.akika.githive.namespace.dto.NamespaceResponse;
import me.akika.githive.namespace.entity.Namespace;

public interface NamespaceService {

    /**
     * 用户注册时创建对应的用户 namespace
     *
     * @param user 刚注册的用户
     * @return 创建的 namespace
     */
    Namespace createUserNamespace(AppUser user);

    /**
     * 根据 path（小写）查询 namespace
     *
     * @param path namespace 路径
     * @return namespace 实体，不存在则返回 null
     */
    Namespace findByPath(String path);

    /**
     * 根据 ID 查询 namespace
     *
     * @param id namespace ID
     * @return namespace 实体，不存在则返回 null
     */
    Namespace getById(Long id);

    /**
     * 检查 path 是否已被占用
     *
     * @param path 要检查的路径（会自动转小写）
     * @return true 表示已被占用
     */
    boolean existsByPath(String path);

    /**
     * 构建 namespace 响应 DTO
     */
    NamespaceResponse toResponse(Namespace namespace);
}
