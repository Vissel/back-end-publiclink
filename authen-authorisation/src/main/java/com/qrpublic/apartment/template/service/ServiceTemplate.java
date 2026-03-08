package com.qrpublic.apartment.template.service;

import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.model.enums.ResultEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
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
        } catch (Throwable e) {
            log.error("[Throwable] message:", e.getMessage());
            result.setErrorCode(Integer.valueOf(ResultEnum.INTERNAL_SERVER_ERROR.getCode()));
            errorMess = "Server error.";
        } finally {
            result.setErrorMessage(errorMess);
        }
        return result;
    }

    private <R> void preProcess(R request) {
    }
}
