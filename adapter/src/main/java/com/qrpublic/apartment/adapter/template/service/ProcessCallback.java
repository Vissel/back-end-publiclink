package com.qrpublic.apartment.adapter.template.service;

public interface ProcessCallback<R, T> {

    R getRequest();

    void preProcess(R request);

    T process();

}
