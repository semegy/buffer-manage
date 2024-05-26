package com;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

public class ServiceRegister implements ImportBeanDefinitionRegistrar {
    /**
     * 方法1： 将带有@MyService注解的类注入到Spring容器
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        //自定义的扫描类MyClassPathBeanDefinitionScanner, 实现了ClassPathBeanDefinitionScanner接口
        // 当前MyClassPathBeanDefinitionScanner已被修改为扫描带有指定注解的类
        ServiceClassPathBeanDefinitionScanner scanner = new ServiceClassPathBeanDefinitionScanner(registry, false);
        scanner.registerFilters(); // 过滤带有注解的类并注入到容器中
        scanner.doScan("com.whut.scaner");
    }
}