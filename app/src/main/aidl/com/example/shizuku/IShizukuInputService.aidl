package com.example.shizuku;

interface IShizukuInputService {
    boolean injectTap(float x, float y, long durationMs);
    boolean isInputReady();
    void destroy();
}
