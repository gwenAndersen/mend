package com.alif.sync.ai;

import com.alif.sync.ai.IAiCallback;

interface IAiService {
    void generateReply(String historyJson, String persona, IAiCallback callback);
}
