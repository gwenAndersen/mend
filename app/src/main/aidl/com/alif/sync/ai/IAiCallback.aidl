package com.alif.sync.ai;

interface IAiCallback {
    void onResponse(String response);
    void onError(String error);
}
