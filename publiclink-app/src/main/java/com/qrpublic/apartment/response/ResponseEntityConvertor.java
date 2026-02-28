package com.qrpublic.apartment.response;

import com.qrpublic.apartment.template.model.Result;
import org.springframework.http.ResponseEntity;

public class ResponseEntityConvertor {

    public static <T> ResponseEntity<Result<T>> convert(Result<T> result){
        if(result.isSuccess()){
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(result.getErrorCode()).body(result);
    }
}
