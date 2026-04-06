package me.akika.githive.namespace.controller;

import lombok.RequiredArgsConstructor;
import me.akika.githive.auth.annotation.Public;
import me.akika.githive.common.api.ApiResponse;
import me.akika.githive.common.exception.BusinessException;
import me.akika.githive.namespace.dto.NamespaceAvailabilityResponse;
import me.akika.githive.namespace.dto.NamespaceResponse;
import me.akika.githive.namespace.entity.Namespace;
import me.akika.githive.namespace.service.NamespaceService;
import org.springframework.web.bind.annotation.*;

@Public
@RestController
@RequestMapping("/api/namespaces")
@RequiredArgsConstructor
public class NamespaceController {

    private final NamespaceService namespaceService;

    /**
     * 查询 namespace 信息
     */
    @GetMapping("/{path}")
    public ApiResponse<NamespaceResponse> getByPath(@PathVariable String path) {
        Namespace namespace = namespaceService.findByPath(path);
        if (namespace == null) {
            throw new BusinessException("命名空间不存在: " + path);
        }
        return ApiResponse.success(namespaceService.toResponse(namespace));
    }

    /**
     * 检查 namespace 可用性
     */
    @GetMapping("/{path}/availability")
    public ApiResponse<NamespaceAvailabilityResponse> checkAvailability(@PathVariable String path) {
        boolean exists = namespaceService.existsByPath(path);
        NamespaceAvailabilityResponse response = NamespaceAvailabilityResponse.builder()
                .path(path.toLowerCase())
                .available(!exists)
                .message(exists ? "命名空间已被占用" : "命名空间可用")
                .build();
        return ApiResponse.success(response);
    }
}
