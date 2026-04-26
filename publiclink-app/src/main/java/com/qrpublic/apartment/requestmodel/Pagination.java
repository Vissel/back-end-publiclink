package com.qrpublic.apartment.requestmodel;

import lombok.Data;

import java.util.List;

@Data
public class Pagination<T> {
    private int page;
    private int size;
    private List<T> listData;
}
