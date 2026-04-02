package me.akika.githive.common.config;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants;
import com.github.xiaoymin.knife4j.core.conf.GlobalConstants;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jHttpBasic;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties;
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jSetting;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@EnableConfigurationProperties({Knife4jProperties.class, Knife4jSetting.class, Knife4jHttpBasic.class})
@ConditionalOnProperty(name = "knife4j.enable", havingValue = "true")
public class Knife4jCompatibilityConfig {

    @Bean("knife4jOpenApiCustomizer")
    @ConditionalOnMissingBean(Knife4jOpenApiCustomizer.class)
    public Knife4jOpenApiCustomizer knife4jOpenApiCustomizer(Knife4jProperties knife4jProperties,
                                                             SpringDocConfigProperties springDocConfigProperties) {
        return new CompatibleKnife4jOpenApiCustomizer(knife4jProperties, springDocConfigProperties);
    }

    @Slf4j
    static class CompatibleKnife4jOpenApiCustomizer extends Knife4jOpenApiCustomizer implements GlobalOpenApiCustomizer {

        private final Knife4jProperties knife4jProperties;
        private final SpringDocConfigProperties properties;

        CompatibleKnife4jOpenApiCustomizer(Knife4jProperties knife4jProperties,
                                           SpringDocConfigProperties properties) {
            super(knife4jProperties, properties);
            this.knife4jProperties = knife4jProperties;
            this.properties = properties;
        }

        @Override
        public void customise(OpenAPI openApi) {
            log.debug("Apply compatible Knife4j OpenAPI customizer");
            if (!knife4jProperties.isEnable()) {
                return;
            }
            Knife4jSetting setting = knife4jProperties.getSetting();
            OpenApiExtensionResolver openApiExtensionResolver =
                    new OpenApiExtensionResolver(setting, knife4jProperties.getDocuments());
            openApiExtensionResolver.start();

            Map<String, Object> objectMap = new HashMap<>();
            objectMap.put(GlobalConstants.EXTENSION_OPEN_SETTING_NAME, setting);
            objectMap.put(GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME, openApiExtensionResolver.getMarkdownFiles());
            openApi.addExtension(GlobalConstants.EXTENSION_OPEN_API_NAME, objectMap);
            addOrderExtension(openApi);
        }

        private void addOrderExtension(OpenAPI openApi) {
            if (CollectionUtils.isEmpty(properties.getGroupConfigs())) {
                return;
            }

            Set<String> packagesToScan = properties.getGroupConfigs().stream()
                    .map(SpringDocConfigProperties.GroupConfig::getPackagesToScan)
                    .filter(toScan -> !CollectionUtils.isEmpty(toScan))
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
            if (CollectionUtils.isEmpty(packagesToScan)) {
                return;
            }

            Set<Class<?>> classes = packagesToScan.stream()
                    .map(packageToScan -> scanPackageByAnnotation(packageToScan, RestController.class))
                    .flatMap(Set::stream)
                    .filter(clazz -> clazz.isAnnotationPresent(ApiSupport.class))
                    .collect(Collectors.toSet());
            if (CollectionUtils.isEmpty(classes)) {
                return;
            }

            Map<String, Integer> tagOrderMap = new HashMap<>();
            classes.forEach(clazz -> {
                Tag tag = getTag(clazz);
                if (Objects.nonNull(tag)) {
                    ApiSupport apiSupport = clazz.getAnnotation(ApiSupport.class);
                    tagOrderMap.putIfAbsent(tag.name(), apiSupport.order());
                }
            });

            if (openApi.getTags() == null) {
                return;
            }

            openApi.getTags().forEach(tag -> {
                if (tagOrderMap.containsKey(tag.getName())) {
                    tag.addExtension(ExtensionsConstants.EXTENSION_ORDER, tagOrderMap.get(tag.getName()));
                }
            });
        }

        private Tag getTag(Class<?> clazz) {
            Tag tag = clazz.getAnnotation(Tag.class);
            if (Objects.nonNull(tag)) {
                return tag;
            }

            Class<?>[] interfaces = clazz.getInterfaces();
            if (ArrayUtils.isEmpty(interfaces)) {
                return null;
            }

            for (Class<?> interfaceClass : interfaces) {
                Tag interfaceTag = interfaceClass.getAnnotation(Tag.class);
                if (Objects.nonNull(interfaceTag)) {
                    return interfaceTag;
                }
            }
            return null;
        }

        private Set<Class<?>> scanPackageByAnnotation(String packageName,
                                                      Class<? extends Annotation> annotationClass) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(annotationClass));

            Set<Class<?>> classes = new HashSet<>();
            for (BeanDefinition beanDefinition : scanner.findCandidateComponents(packageName)) {
                try {
                    classes.add(Class.forName(beanDefinition.getBeanClassName()));
                } catch (ClassNotFoundException ignored) {
                    log.debug("Skip unloaded class {}", beanDefinition.getBeanClassName());
                }
            }
            return classes;
        }
    }
}
