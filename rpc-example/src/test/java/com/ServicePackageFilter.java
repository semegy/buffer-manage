package com;

import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

public class ServicePackageFilter implements TypeFilter {
    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
//        为了用户无感知，不用过滤出带有@Myservice的类，直接处理basePackages下面的所有类
//        return metadataReader.getAnnotationMetadata()
//                .hasAnnotation("com.whut.scanner.service");
        return true;
    }
}