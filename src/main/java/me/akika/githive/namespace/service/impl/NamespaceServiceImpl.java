package me.akika.githive.namespace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.entity.AppUser;
import me.akika.githive.common.exception.BusinessException;
import me.akika.githive.common.state.NamespaceType;
import me.akika.githive.namespace.dto.NamespaceResponse;
import me.akika.githive.namespace.entity.Namespace;
import me.akika.githive.namespace.mapper.NamespaceMapper;
import me.akika.githive.namespace.service.NamespaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NamespaceServiceImpl implements NamespaceService {

    private final NamespaceMapper namespaceMapper;

    @Override
    @Transactional
    public Namespace createUserNamespace(AppUser user) {
        String path = user.getUsername().toLowerCase(Locale.ROOT);

        if (existsByPath(path)) {
            throw new BusinessException("命名空间已被占用: " + path);
        }

        LocalDateTime now = LocalDateTime.now();
        Namespace namespace = Namespace.builder()
                .path(path)
                .displayPath(user.getUsername())
                .ownerType(NamespaceType.USER)
                .ownerId(user.getId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        namespaceMapper.insert(namespace);
        return namespace;
    }

    @Override
    public Namespace findByPath(String path) {
        if (path == null) {
            return null;
        }
        return namespaceMapper.selectOne(
                new LambdaQueryWrapper<Namespace>()
                        .eq(Namespace::getPath, path.toLowerCase(Locale.ROOT)));
    }

    @Override
    public Namespace getById(Long id) {
        return namespaceMapper.selectById(id);
    }

    @Override
    public boolean existsByPath(String path) {
        if (path == null) {
            return false;
        }
        return namespaceMapper.selectCount(
                new LambdaQueryWrapper<Namespace>()
                        .eq(Namespace::getPath, path.toLowerCase(Locale.ROOT))) > 0;
    }

    @Override
    public NamespaceResponse toResponse(Namespace namespace) {
        return NamespaceResponse.builder()
                .id(namespace.getId())
                .path(namespace.getPath())
                .displayPath(namespace.getDisplayPath())
                .ownerType(namespace.getOwnerType())
                .ownerId(namespace.getOwnerId())
                .createdAt(namespace.getCreatedAt())
                .build();
    }
}
