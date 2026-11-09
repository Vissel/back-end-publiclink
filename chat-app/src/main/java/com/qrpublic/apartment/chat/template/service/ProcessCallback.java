package com.qrpublic.apartment.chat.template.service;

public interface ProcessCallback<R, T> {

    R getRequest();

    void preProcess(R request);

    T process();
}
