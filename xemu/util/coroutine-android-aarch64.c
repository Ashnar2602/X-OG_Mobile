/*
 * Android AArch64 coroutine backend.
 *
 * Bionic's siglongjmp() validates pointer-authenticated state and aborts the
 * sigaltstack coroutine trick used by the generic POSIX backend.  This backend
 * performs a small callee-saved register context switch directly.
 */

#include "qemu/osdep.h"
#include <pthread.h>
#include "qemu/coroutine_int.h"

typedef struct AndroidAarch64Context {
    uint64_t x19;
    uint64_t x20;
    uint64_t x21;
    uint64_t x22;
    uint64_t x23;
    uint64_t x24;
    uint64_t x25;
    uint64_t x26;
    uint64_t x27;
    uint64_t x28;
    uint64_t x29;
    uint64_t x30;
    uint64_t sp;
    uint64_t pc;
} AndroidAarch64Context;

typedef struct CoroutineAndroidAarch64 {
    Coroutine base;
    void *stack;
    size_t stack_size;
    AndroidAarch64Context ctx;
} CoroutineAndroidAarch64;

typedef struct CoroutineThreadState {
    Coroutine *current;
    CoroutineAndroidAarch64 leader;
} CoroutineThreadState;

static pthread_key_t thread_state_key;

extern CoroutineAction qemu_android_aarch64_switch(AndroidAarch64Context *from,
                                                   AndroidAarch64Context *to,
                                                   CoroutineAction action);
extern void qemu_android_aarch64_trampoline(void);

static CoroutineThreadState *coroutine_get_thread_state(void)
{
    CoroutineThreadState *s = pthread_getspecific(thread_state_key);

    if (!s) {
        s = g_malloc0(sizeof(*s));
        s->current = &s->leader.base;
        pthread_setspecific(thread_state_key, s);
    }
    return s;
}

static void qemu_coroutine_thread_cleanup(void *opaque)
{
    g_free(opaque);
}

static void __attribute__((constructor)) coroutine_init(void)
{
    int ret = pthread_key_create(&thread_state_key,
                                 qemu_coroutine_thread_cleanup);
    if (ret != 0) {
        fprintf(stderr, "unable to create coroutine key: %s\n", strerror(ret));
        abort();
    }
}

void qemu_android_aarch64_bootstrap(CoroutineAndroidAarch64 *self)
{
    Coroutine *co = &self->base;

    while (true) {
        co->entry(co->entry_arg);
        qemu_coroutine_switch(co, co->caller, COROUTINE_TERMINATE);
    }
}

Coroutine *qemu_coroutine_new(void)
{
    CoroutineAndroidAarch64 *co = g_malloc0(sizeof(*co));
    uintptr_t sp;

    co->stack_size = COROUTINE_STACK_SIZE;
    co->stack = qemu_alloc_stack(&co->stack_size);

    sp = (uintptr_t)co->stack + co->stack_size;
    sp &= ~(uintptr_t)0xf;
    sp -= 16;

    co->ctx.x19 = (uintptr_t)co;
    co->ctx.sp = sp;
    co->ctx.pc = (uintptr_t)qemu_android_aarch64_trampoline;

    return &co->base;
}

void qemu_coroutine_delete(Coroutine *co_)
{
    CoroutineAndroidAarch64 *co =
        DO_UPCAST(CoroutineAndroidAarch64, base, co_);

    qemu_free_stack(co->stack, co->stack_size);
    g_free(co);
}

CoroutineAction qemu_coroutine_switch(Coroutine *from_, Coroutine *to_,
                                      CoroutineAction action)
{
    CoroutineAndroidAarch64 *from =
        DO_UPCAST(CoroutineAndroidAarch64, base, from_);
    CoroutineAndroidAarch64 *to =
        DO_UPCAST(CoroutineAndroidAarch64, base, to_);
    CoroutineThreadState *s = coroutine_get_thread_state();

    s->current = to_;
    return qemu_android_aarch64_switch(&from->ctx, &to->ctx, action);
}

Coroutine *qemu_coroutine_self(void)
{
    CoroutineThreadState *s = coroutine_get_thread_state();

    return s->current;
}

bool qemu_in_coroutine(void)
{
    CoroutineThreadState *s = pthread_getspecific(thread_state_key);

    return s && s->current->caller;
}

__asm__(
".text\n"
".balign 4\n"
".global qemu_android_aarch64_switch\n"
".type qemu_android_aarch64_switch, %function\n"
"qemu_android_aarch64_switch:\n"
"    stp x19, x20, [x0, #0]\n"
"    stp x21, x22, [x0, #16]\n"
"    stp x23, x24, [x0, #32]\n"
"    stp x25, x26, [x0, #48]\n"
"    stp x27, x28, [x0, #64]\n"
"    str x29, [x0, #80]\n"
"    str x30, [x0, #88]\n"
"    mov x9, sp\n"
"    str x9, [x0, #96]\n"
"    adr x9, 1f\n"
"    str x9, [x0, #104]\n"
"    ldp x19, x20, [x1, #0]\n"
"    ldp x21, x22, [x1, #16]\n"
"    ldp x23, x24, [x1, #32]\n"
"    ldp x25, x26, [x1, #48]\n"
"    ldp x27, x28, [x1, #64]\n"
"    ldr x29, [x1, #80]\n"
"    ldr x30, [x1, #88]\n"
"    ldr x9, [x1, #96]\n"
"    mov sp, x9\n"
"    ldr x9, [x1, #104]\n"
"    mov x0, x2\n"
"    br x9\n"
"1:\n"
"    ret\n"
".size qemu_android_aarch64_switch, .-qemu_android_aarch64_switch\n"
".balign 4\n"
".global qemu_android_aarch64_trampoline\n"
".type qemu_android_aarch64_trampoline, %function\n"
"qemu_android_aarch64_trampoline:\n"
"    mov x0, x19\n"
"    bl qemu_android_aarch64_bootstrap\n"
"    brk #0\n"
".size qemu_android_aarch64_trampoline, .-qemu_android_aarch64_trampoline\n"
);
