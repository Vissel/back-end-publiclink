package com.qrpublic.apartment.adapter.template.service;

import com.qrpublic.apartment.adapter.exception.BusinessException;
import com.qrpublic.apartment.adapter.template.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceTemplate {
    public <R, T> Result<T> execute(ProcessCallback<R, T> callback) {
        Result<T> result = new Result<>();
        String errorMess = null;
        result.setSuccess(false);
        try {
            callback.preProcess(callback.getRequest());
            T data = callback.process();
            result.setData(data);
            result.setSuccess(true);
        } catch (BusinessException e) {
            log.error("[BusinessException] message:", e.getMessage());
            result.setErrorCode(e.getErrorCode());
            errorMess = e.getMessage();
        } catch (Throwable e) {
            log.error("[Throwable] message:", e.getMessage());
            result.setErrorCode(500);
            errorMess = "Server error.";
        } finally {
            result.setErrorMessage(errorMess);
        }
        return result;
    }

    private <R> void preProcess(R request) {
    }
}
