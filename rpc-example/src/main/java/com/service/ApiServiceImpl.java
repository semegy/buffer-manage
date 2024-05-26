package com.service;

import com.Api;
import com.ProviderService;

@ProviderService
public class ApiServiceImpl implements Api {


    @Override
    public String test(String s) {
        return s;
    }
}
