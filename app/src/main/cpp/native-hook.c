#include <jni.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#define TAG "LowiroFucker"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// ── Configuration (set from Java) ──────────────────────────────────────────
static volatile uint32_t g_buffer_length = 1024;
static volatile int32_t  g_num_buffers   = 4;

// ── Hook state ─────────────────────────────────────────────────────────────
static void    *g_trampoline_setDSP = NULL;
static void    *g_trampoline_init   = NULL;
static void    *g_trampoline_setFmt  = NULL;
static int      g_hooks_installed   = 0;

// Call counters
static volatile int32_t g_cnt_setDSP = 0;
static volatile int32_t g_cnt_init   = 0;
static volatile int32_t g_cnt_setFmt = 0;

// Saved original function pointers (for trampoline calls)
static void *g_orig_setDSP = NULL;
static void *g_orig_init   = NULL;
static void *g_orig_setFmt = NULL;

// dlopen hook state
static void    *g_trampoline_dlopen = NULL;
static void    *g_orig_dlopen       = NULL;
static int      g_dlopen_hooked     = 0;

// ── ARM64 inline hook helper ───────────────────────────────────────────────
static int do_patch(void *target, void *hook_fn, void **out_trampoline) {
    if (!target || !hook_fn) return -1;

    uint8_t orig_bytes[16];
    memcpy(orig_bytes, target, 16);

    long ps = sysconf(_SC_PAGESIZE);
    void *tramp = mmap(NULL, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC,
                       MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (tramp == MAP_FAILED) return -2;

    // trampoline: [original 16] + [LDR X17,#8; BR X17; .quad target+16]
    memcpy(tramp, orig_bytes, 16);
    uint32_t *tj = (uint32_t *)((uintptr_t)tramp + 16);
    tj[0] = 0x58000051; tj[1] = 0xD61F0220;
    *(uint64_t *)(tj + 2) = (uintptr_t)target + 16;

    uintptr_t pg = (uintptr_t)target & ~(uintptr_t)(ps - 1);
    if (mprotect((void *)pg, (size_t)ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0)
        return -3;

    uint32_t *p = (uint32_t *)target;
    p[0] = 0x58000051; p[1] = 0xD61F0220;
    *(uint64_t *)(p + 2) = (uintptr_t)hook_fn;

    mprotect((void *)pg, (size_t)ps, PROT_READ | PROT_EXEC);
    __builtin___clear_cache((char *)target, (char *)target + 16);
    __builtin___clear_cache((char *)tramp, (char *)tramp + 32);

    *out_trampoline = tramp;
    return 0;
}

// ── FMOD hook functions ────────────────────────────────────────────────────

// FMOD::System::setDSPBufferSize(unsigned int, int)
typedef int32_t (*fn_setDSP)(void *, uint32_t, int32_t);
static int32_t hk_setDSP(void *sys, uint32_t bufLen, int32_t numBufs) {
    g_cnt_setDSP++;
    uint32_t ob = bufLen; int32_t on = numBufs;
    bufLen = g_buffer_length; numBufs = g_num_buffers;
    LOGI("[FMOD-NATIVE] setDSPBufferSize #%d: bufLen %u->%u numBufs %d->%d",
         g_cnt_setDSP, ob, bufLen, on, numBufs);
    return ((fn_setDSP)g_trampoline_setDSP)(sys, bufLen, numBufs);
}

// FMOD::System::init(int maxchannels, unsigned int flags, void *extradriverdata)
typedef int32_t (*fn_init)(void *, int32_t, uint32_t, void *);
static int32_t hk_init(void *sys, int32_t ch, uint32_t flags, void *extra) {
    g_cnt_init++;
    LOGI("[FMOD-NATIVE] System::init #%d ch=%d flags=0x%x", g_cnt_init, ch, flags);
    // Force setDSPBufferSize BEFORE init
    if (g_trampoline_setDSP) {
        LOGI("[FMOD-NATIVE] Pre-init: setDSPBufferSize(%u, %d)", g_buffer_length, g_num_buffers);
        ((fn_setDSP)g_trampoline_setDSP)(sys, g_buffer_length, g_num_buffers);
    }
    return ((fn_init)g_trampoline_init)(sys, ch, flags, extra);
}

// FMOD::System::setSoftwareFormat(int samplerate, FMOD_SPEAKERMODE, int numrawspeakers)
typedef int32_t (*fn_setFmt)(void *, int32_t, int32_t, int32_t);
static int32_t hk_setFmt(void *sys, int32_t rate, int32_t mode, int32_t spk) {
    g_cnt_setFmt++;
    LOGI("[FMOD-NATIVE] setSoftwareFormat #%d rate=%d mode=%d speakers=%d",
         g_cnt_setFmt, rate, mode, spk);
    return ((fn_setFmt)g_trampoline_setFmt)(sys, rate, mode, spk);
}

// ── Install all FMOD hooks on a given handle ───────────────────────────────
static void try_install_fmod_hooks(void *handle) {
    if (g_hooks_installed) return;

    void *fn;

    fn = dlsym(handle, "_ZN4FMOD6System16setDSPBufferSizeEji");
    if (!fn) fn = dlsym(handle, "._ZN4FMOD6System16setDSPBufferSizeEji");
    if (fn) {
        LOGI("[FMOD-NATIVE] setDSPBufferSize @ %p — patching", fn);
        g_orig_setDSP = fn;
        do_patch(fn, (void *)hk_setDSP, &g_trampoline_setDSP);
    }

    fn = dlsym(handle, "_ZN4FMOD6System4initEijPv");
    if (!fn) fn = dlsym(handle, "._ZN4FMOD6System4initEijPv");
    if (fn) {
        LOGI("[FMOD-NATIVE] System::init @ %p — patching", fn);
        g_orig_init = fn;
        do_patch(fn, (void *)hk_init, &g_trampoline_init);
    }

    fn = dlsym(handle, "_ZN4FMOD6System17setSoftwareFormatEi20FMOD_SPEAKERMODEi");
    if (!fn) fn = dlsym(handle, "._ZN4FMOD6System17setSoftwareFormatEi20FMOD_SPEAKERMODEi");
    if (fn) {
        LOGI("[FMOD-NATIVE] setSoftwareFormat @ %p — patching", fn);
        g_orig_setFmt = fn;
        do_patch(fn, (void *)hk_setFmt, &g_trampoline_setFmt);
    }

    g_hooks_installed = (g_trampoline_setDSP || g_trampoline_init || g_trampoline_setFmt) ? 1 : 0;
    if (g_hooks_installed)
        LOGI("[FMOD-NATIVE] FMOD hooks installed! bufLen=%u numBufs=%d", g_buffer_length, g_num_buffers);
}

// ── dlopen hook: intercept libfmod.so loading ──────────────────────────────
typedef void *(*fn_dlopen)(const char *, int);
static void *hk_dlopen(const char *filename, int flags) {
    void *handle = ((fn_dlopen)g_trampoline_dlopen)(filename, flags);
    if (filename && strstr(filename, "libfmod.so") && !g_hooks_installed) {
        LOGI("[FMOD-NATIVE] dlopen intercepted: %s -> installing FMOD hooks", filename);
        try_install_fmod_hooks(handle);
    }
    return handle;
}

static void install_dlopen_hook() {
    void *sym = dlsym(RTLD_DEFAULT, "android_dlopen_ext");
    if (!sym) {
        // Try libdl
        void *libdl = dlopen("libdl.so", RTLD_NOW);
        if (libdl) sym = dlsym(libdl, "android_dlopen_ext");
    }
    if (!sym) {
        LOGW("[FMOD-NATIVE] android_dlopen_ext not found");
        return;
    }
    LOGI("[FMOD-NATIVE] android_dlopen_ext @ %p — patching", sym);
    g_orig_dlopen = sym;
    int ret = do_patch(sym, (void *)hk_dlopen, &g_trampoline_dlopen);
    g_dlopen_hooked = (ret == 0) ? 1 : 0;
    LOGI("[FMOD-NATIVE] dlopen hook: %s", ret == 0 ? "OK" : "FAILED");
}

// ── JNI exports ────────────────────────────────────────────────────────────

static char *find_loaded_library(const char *lib_name) {
    FILE *fp = fopen("/proc/self/maps", "r");
    if (!fp) return NULL;
    char line[512]; char *result = NULL;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, lib_name)) {
            char *p = strstr(line, "/");
            if (p) {
                char *e = strchr(p, '\n'); if (e) *e = '\0';
                e = strchr(p, ' '); if (e) *e = '\0';
                result = strdup(p); break;
            }
        }
    }
    fclose(fp);
    return result;
}

JNIEXPORT void JNICALL
Java_com_nsct_lowirofucker_NativeHook_nativeInit(JNIEnv *env, jclass clazz) {
    LOGI("[FMOD-NATIVE] nativeInit: installing dlopen hook");
    install_dlopen_hook();
}

JNIEXPORT jint JNICALL
Java_com_nsct_lowirofucker_NativeHook_nativeInstallHook(
        JNIEnv *env, jclass clazz, jstring lib_name, jint buf_len, jint num_bufs) {

    g_buffer_length = (uint32_t)buf_len;
    g_num_buffers   = (int32_t)num_bufs;

    if (g_hooks_installed) {
        LOGI("[FMOD-NATIVE] Already hooked, config: bufLen=%u numBufs=%d",
             g_buffer_length, g_num_buffers);
        return 0;
    }

    const char *name = (*env)->GetStringUTFChars(env, lib_name, NULL);
    void *handle = dlopen(name, RTLD_NOW);
    if (!handle) {
        char *fp = find_loaded_library(name);
        if (fp) { handle = dlopen(fp, RTLD_NOW); free(fp); }
    }
    (*env)->ReleaseStringUTFChars(env, lib_name, name);

    if (!handle) { LOGW("[FMOD-NATIVE] dlopen failed"); return -1; }

    try_install_fmod_hooks(handle);
    return g_hooks_installed ? 0 : -2;
}

JNIEXPORT void JNICALL
Java_com_nsct_lowirofucker_NativeHook_nativeUpdateConfig(
        JNIEnv *env, jclass clazz, jint buf_len, jint num_bufs) {
    g_buffer_length = (uint32_t)buf_len;
    g_num_buffers   = (int32_t)num_bufs;
    LOGI("[FMOD-NATIVE] Config: bufLen=%u numBufs=%d", g_buffer_length, g_num_buffers);
}

JNIEXPORT jboolean JNICALL
Java_com_nsct_lowirofucker_NativeHook_nativeIsHookInstalled(JNIEnv *env, jclass c) {
    return g_hooks_installed ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_nsct_lowirofucker_NativeHook_nativeGetHookCallCount(JNIEnv *env, jclass c) {
    return (jint)(g_cnt_setDSP + g_cnt_init + g_cnt_setFmt);
}
