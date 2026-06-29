/*
 * Android SAF file descriptor block protocol.
 *
 * This driver lets the Android frontend pass an already-open file descriptor
 * to QEMU without reopening /proc/self/fd or the underlying /storage path.
 *
 * Copyright (C) 2026 X-OG Mobile contributors
 *
 * This work is licensed under the terms of the GNU GPL, version 2 or later.
 * See the COPYING file in the top-level directory.
 */

#include "qemu/osdep.h"
#include "qapi/error.h"
#include "qobject/qdict.h"
#include "qemu/module.h"
#include "block/block_int.h"

typedef struct BDRVAndroidFdState {
    int fd;
    int64_t length;
} BDRVAndroidFdState;

static void androidfd_parse_filename(const char *filename, QDict *options,
                                     Error **errp)
{
    const char prefix[] = "androidfd:";
    const char *fd_text;

    if (strncmp(filename, prefix, sizeof(prefix) - 1) != 0) {
        error_setg(errp, "Android fd images must use androidfd:<fd>");
        return;
    }
    fd_text = filename + sizeof(prefix) - 1;
    if (!fd_text[0]) {
        error_setg(errp, "Android fd images must include a file descriptor");
        return;
    }
    qdict_put_str(options, "fd", fd_text);
}

static int androidfd_open(BlockDriverState *bs, QDict *options, int flags,
                          Error **errp)
{
    BDRVAndroidFdState *s = bs->opaque;
    const char *fd_text = qdict_get_try_str(options, "fd");
    char *end = NULL;
    struct stat st;
    long source_fd;

    if (!fd_text || !fd_text[0]) {
        error_setg(errp, "Missing Android file descriptor");
        return -EINVAL;
    }

    errno = 0;
    source_fd = strtol(fd_text, &end, 10);
    if (errno || end == fd_text || *end != '\0' || source_fd < 0 ||
        source_fd > INT_MAX) {
        error_setg(errp, "Invalid Android file descriptor '%s'", fd_text);
        return -EINVAL;
    }
    qdict_del(options, "fd");

    s->fd = dup((int)source_fd);
    if (s->fd < 0) {
        int ret = -errno;
        error_setg_errno(errp, -ret, "Failed to duplicate Android fd");
        return ret;
    }

    if (fstat(s->fd, &st) == 0 && S_ISREG(st.st_mode)) {
        s->length = st.st_size;
    } else {
        off_t cur = lseek(s->fd, 0, SEEK_CUR);
        off_t end_pos = lseek(s->fd, 0, SEEK_END);
        if (cur >= 0) {
            lseek(s->fd, cur, SEEK_SET);
        }
        if (end_pos < 0) {
            int ret = -errno;
            close(s->fd);
            s->fd = -1;
            error_setg_errno(errp, -ret, "Failed to determine Android fd size");
            return ret;
        }
        s->length = end_pos;
    }

    snprintf(bs->exact_filename, sizeof(bs->exact_filename), "androidfd:<open>");
    return 0;
}

static void androidfd_close(BlockDriverState *bs)
{
    BDRVAndroidFdState *s = bs->opaque;

    if (s->fd >= 0) {
        close(s->fd);
        s->fd = -1;
    }
}

static int64_t coroutine_fn androidfd_co_getlength(BlockDriverState *bs)
{
    BDRVAndroidFdState *s = bs->opaque;

    return s->length;
}

static int coroutine_fn androidfd_co_preadv(BlockDriverState *bs,
                                            int64_t offset, int64_t bytes,
                                            QEMUIOVector *qiov,
                                            BdrvRequestFlags flags)
{
    BDRVAndroidFdState *s = bs->opaque;
    int64_t remaining = bytes;
    int64_t current_offset = offset;

    for (int i = 0; i < qiov->niov && remaining > 0; i++) {
        char *base = qiov->iov[i].iov_base;
        size_t todo = MIN((int64_t)qiov->iov[i].iov_len, remaining);

        while (todo > 0) {
            ssize_t count = pread(s->fd, base, todo, current_offset);
            if (count < 0) {
                if (errno == EINTR) {
                    continue;
                }
                return -errno;
            }
            if (count == 0) {
                return -EIO;
            }
            base += count;
            todo -= count;
            remaining -= count;
            current_offset += count;
        }
    }

    return remaining == 0 ? 0 : -EIO;
}

static int coroutine_fn androidfd_co_pwritev(BlockDriverState *bs,
                                             int64_t offset, int64_t bytes,
                                             QEMUIOVector *qiov,
                                             BdrvRequestFlags flags)
{
    return -ENOTSUP;
}

static int64_t coroutine_fn
androidfd_co_get_allocated_file_size(BlockDriverState *bs)
{
    BDRVAndroidFdState *s = bs->opaque;

    return s->length;
}

static BlockDriver bdrv_androidfd = {
    .format_name = "androidfd",
    .protocol_name = "androidfd",
    .instance_size = sizeof(BDRVAndroidFdState),
    .bdrv_parse_filename = androidfd_parse_filename,
    .bdrv_open = androidfd_open,
    .bdrv_close = androidfd_close,
    .bdrv_co_getlength = androidfd_co_getlength,
    .bdrv_co_get_allocated_file_size = androidfd_co_get_allocated_file_size,
    .bdrv_co_preadv = androidfd_co_preadv,
    .bdrv_co_pwritev = androidfd_co_pwritev,
};

static void bdrv_androidfd_init(void)
{
    bdrv_register(&bdrv_androidfd);
}

block_init(bdrv_androidfd_init);
