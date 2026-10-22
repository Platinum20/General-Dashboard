#!/usr/bin/env python3
"""Scarica le liste di filtri (EasyList, EasyPrivacy, uBlock Origin) dentro l'APK.

Le liste incluse servono al primo avvio e senza rete: poi l'app le aggiorna da sola.
Uso: python3 scripts/update_filter_lists.py   (dalla cartella del progetto)
Se un download fallisce la lista già presente resta com'è.
"""
import os
import re
import sys
import urllib.request

DEST = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "adblock", "lists")
UBO = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/"
LISTS = {
    "easylist.txt": ["https://raw.githubusercontent.com/easylist/easylist/gh-pages/easylist.txt"],
    "easyprivacy.txt": ["https://raw.githubusercontent.com/easylist/easylist/gh-pages/easyprivacy.txt"],
    "ublock-filters.txt": [UBO + "filters.txt"],
    "ublock-privacy.txt": [UBO + "privacy.txt"],
    "ublock-quick-fixes.txt": [UBO + "quick-fixes.txt"],
    "ublock-unbreak.txt": [UBO + "unbreak.txt"],
}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "General-Dashboard"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def expand(text, base, depth=0):
    """Sostituisce le righe "!#include file" con il contenuto del file (come fa uBlock Origin)."""
    out = []
    for line in text.splitlines():
        m = re.match(r"^!#include\s+(\S+)", line)
        if m and depth < 3:
            url = base + m.group(1)
            out.append(expand(fetch(url), url.rsplit("/", 1)[0] + "/", depth + 1))
        else:
            out.append(line)
    return "\n".join(out)


def main():
    os.makedirs(DEST, exist_ok=True)
    failed = 0
    for name, urls in LISTS.items():
        for url in urls:
            try:
                text = expand(fetch(url), url.rsplit("/", 1)[0] + "/")
                if len(text) < 10_000:
                    raise ValueError("lista troppo corta")
                with open(os.path.join(DEST, name), "w", encoding="utf-8") as f:
                    f.write(text if text.endswith("\n") else text + "\n")
                print(f"{name}: {len(text) // 1024} KB")
                break
            except Exception as e:  # noqa: BLE001
                print(f"{name}: {url} non raggiungibile ({e})", file=sys.stderr)
        else:
            failed += 1
    print("Liste aggiornate." if not failed else f"{failed} liste non aggiornate, resta la copia precedente.")


if __name__ == "__main__":
    main()
