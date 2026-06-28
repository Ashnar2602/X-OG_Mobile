#include "qemu/osdep.h"
#include "qapi/error.h"
#include "net/clients.h"

int net_init_pcap(const Netdev *netdev, const char *name,
                  NetClientState *peer, Error **errp)
{
    error_setg(errp, "pcap networking is not available on Android");
    return -1;
}
