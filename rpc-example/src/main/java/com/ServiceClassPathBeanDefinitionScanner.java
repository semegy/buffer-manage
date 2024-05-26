package com;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ServiceClassPathBeanDefinitionScanner extends ClassPathBeanDefinitionScanner {
    public ServiceClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
        super(registry, useDefaultFilters);
    }

    /**
     * @addIncludeFilter 将自定义的注解添加到扫描任务中
     */
    protected void registerFilters() {
        /**
         *  注入@MyService注解标记的类
         */
        addIncludeFilter(new AnnotationTypeFilter(ProviderService.class));
    }


    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        super.setResourceLoader(resourceLoader);
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitionHolders = super.doScan(basePackages);
        Iterator<BeanDefinitionHolder> iterator = beanDefinitionHolders.iterator();
        HashSet<BeanDefinitionHolder> newHolders = new HashSet<>();
        while (iterator.hasNext()) {
            BeanDefinitionHolder next = iterator.next();
            BeanDefinition beanDefinition = next.getBeanDefinition();
            String beanClassName = beanDefinition.getBeanClassName();
            try {
                Class<?> beanClass = Class.forName(beanClassName);
                ProviderService server = beanClass.getDeclaredAnnotation(ProviderService.class);
                if (server != null) {
                    AnnotationAttributes attributes = AnnotationUtils.getAnnotationAttributes(server, false, false);
                    BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(ServiceBean.class);
                    AbstractBeanDefinition bd = builder.getBeanDefinition();
                    MutablePropertyValues propertyValues = bd.getPropertyValues();
                    RuntimeBeanReference runtimeBeanReference = new RuntimeBeanReference(next.getBeanName());
                    propertyValues.add("ref", runtimeBeanReference);
                    Class<?>[] interfaces = beanClass.getInterfaces();
                    String interfaceClass = attributes.getClass("interfaceClass").getName();
                    Method[] methods = null;
                    if (interfaceClass == "void") {
                        propertyValues.add("interfaceName", interfaces[0].getName());
                        methods = interfaces[0].getDeclaredMethods();
                    } else {
                        for (Class<?> anInterface : interfaces) {
                            if (anInterface.getName().equals(attributes.getClass("interfaceClass").getName())) {
                                propertyValues.add("interfaceName", interfaceClass);
                                methods = interfaces[0].getDeclaredMethods();
                                break;
                            }
                        }
                    }
                    propertyValues.add("methods", methods);
                    String beanName = next.getBeanName() + "##" + ServiceBean.class.getName();
                    if (checkCandidate(beanName, builder.getBeanDefinition())) {
                        BeanDefinitionHolder beanDefinitionHolder = new BeanDefinitionHolder(builder.getBeanDefinition(), next.getBeanName() + "##" + ServiceBean.class.getName());
                        registerBeanDefinition(beanDefinitionHolder, this.getRegistry());
                        newHolders.add(beanDefinitionHolder);
                    }
                } else {
                    iterator.remove();
                }

            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        newHolders.addAll(beanDefinitionHolders);
        return newHolders;
    }
}