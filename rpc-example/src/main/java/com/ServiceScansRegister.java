package com;

import com.provider.ServiceClassPathBeanDefinitionScanner;
import com.provider.ServicePackageFilter;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ServiceScansRegister implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry beanDefinitionRegistry) {
        AnnotationAttributes annotationAttrs = AnnotationAttributes
                .fromMap(annotationMetadata.getAnnotationAttributes(ServiceScans.class.getName()));
        if (annotationAttrs == null) {
            return;
        }
        //构造扫描器，并将spring的beanDefinitionRegistry注入到扫描器内，方便将扫描出的BeanDefinition注入进入beanDefinitionRegistry
        ServiceClassPathBeanDefinitionScanner scanner = new ServiceClassPathBeanDefinitionScanner(beanDefinitionRegistry, false);

        List<String> basePackages = new ArrayList<>();
        for (String pkg : annotationAttrs.getStringArray("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg);
            }
        }
        //添加相关过滤器（为了用户无感知，不过滤@MyService注解，直接处理basePackages下面的所有类）
        scanner.addIncludeFilter(new ServicePackageFilter());
        //扫描并注入
        scanner.doScan(StringUtils.toStringArray(basePackages));
    }
}