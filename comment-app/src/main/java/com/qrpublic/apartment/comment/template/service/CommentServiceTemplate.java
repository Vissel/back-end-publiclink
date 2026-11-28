package com.qrpublic.apartment.comment.template.service;

import com.qrpublic.apartment.comment.exception.ApplicationException;
import com.qrpublic.apartment.comment.template.model.Result;
import com.qrpublic.apartment.comment.template.model.enums.ResultEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The comment module's service template: validation in preProcess, work in
 * process, every failure mapped onto the shared {@link Result} error contract
 * so the controllers stay thin and the HTTP status stays meaningful.
 */
@Slf4j
@Service
public class CommentServiceTemplate {

    public <R, T> Result<T> execute(ProcessCallback<R, T> callback) {
        Result<T> result = new Result<>();
        String errorMess = null;
        result.setSuccess(false);
        try {
            callback.preProcess(callback.getRequest());
            T data = callback.process();
            result.setData(data);
            result.setSuccess(true);
        } catch (IllegalArgumentException e) {
            log.error("[IllegalArgumentException] message: {}", e.getMessage(), e);
            result.setErrorCode(HttpStatus.BAD_REQUEST.value());
            errorMess = e.getMessage();
        } catch (ApplicationException e) {
            log.error("[ApplicationException] message: {}", e.getMessage(), e);
            result.setErrorCode(e.getErrorCode());
            errorMess = e.getMessage();
        } catch (Throwable e) {
            log.error("[Throwable] message: {}", e.getMessage(), e);
            result.setErrorCode(Integer.valueOf(ResultEnum.INTERNAL_SERVER_ERROR.getCode()));
            errorMess = "Server error.";
        } finally {
            result.setErrorMessage(errorMess);
        }
        return result;
    }
}
