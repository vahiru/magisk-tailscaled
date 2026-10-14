package io.github.vahiru.tailscaled.data

object Fixtures {
    const val STATUS_JSON = """
    {
      "Version": "1.98.8-td6a741131",
      "BackendState": "Running",
      "AuthURL": "",
      "TailscaleIPs": ["100.64.0.5", "fd7a:115c:a1e0::5"],
      "Self": {
        "ID": "nSelf", "HostName": "pixel", "DNSName": "pixel.tail1234.ts.net.", "OS": "android",
        "UserID": 42, "TailscaleIPs": ["100.64.0.5", "fd7a:115c:a1e0::5"], "Online": true,
        "LastSeen": "0001-01-01T00:00:00Z", "Relay": "hkg", "CurAddr": "", "PrimaryRoutes": null, "Tags": null
      },
      "Health": ["Tailscale DNS is not configured"],
      "MagicDNSSuffix": "tail1234.ts.net",
      "CurrentTailnet": {"Name": "me@example.com", "MagicDNSSuffix": "tail1234.ts.net", "MagicDNSEnabled": true},
      "Peer": {
        "nodekey:aaa": {
          "ID": "n1", "HostName": "NAS", "DNSName": "nas.tail1234.ts.net.", "OS": "linux", "UserID": 42,
          "TailscaleIPs": ["100.64.0.10", "fd7a:115c:a1e0::a"], "Online": true, "Active": true,
          "ExitNode": true, "ExitNodeOption": true, "Relay": "hkg", "CurAddr": "192.168.1.10:41641",
          "RxBytes": 2048, "TxBytes": 1024, "LastSeen": "2026-10-14T01:00:00Z",
          "PrimaryRoutes": ["192.168.1.0/24", "0.0.0.0/0", "::/0"], "Tags": ["tag:server"]
        },
        "nodekey:bbb": {
          "ID": "n2", "HostName": "old-laptop", "DNSName": "old-laptop.tail1234.ts.net.", "OS": "windows",
          "UserID": 42, "TailscaleIPs": ["100.64.0.11"], "Online": false, "LastSeen": "2026-10-01T10:00:00Z"
        }
      },
      "User": {"42": {"ID": 42, "LoginName": "me@example.com", "DisplayName": "Me"}},
      "ExitNodeStatus": {"ID": "n1", "Online": true, "TailscaleIPs": ["100.64.0.10/32"]}
    }
    """
}
