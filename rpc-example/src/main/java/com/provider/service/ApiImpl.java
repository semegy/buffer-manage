package com.provider.service;

@ProviderService
public class ApiImpl implements Api {
    @Override
    public String test(String s) {
        return s;
    }
}
