package com.qrpublic.apartment.service;

import com.qrpublic.apartment.entity.ProcessMessage;
import com.qrpublic.apartment.repository.ProcessMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotencyService {
    @Autowired
    ProcessMessageRepository processMessageRepository;

    public boolean isProcessed(String msgId) {
        Optional<ProcessMessage> opt = processMessageRepository.findById(msgId);
        return opt.isPresent();
    }

    public void markProcessed(String msgId) {

    }
}
